package com.haulmont.taskman.core;

import com.haulmont.addon.imap.api.ImapAPI;
import com.haulmont.addon.imap.dto.ImapMessageDto;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.events.NewEmailImapEvent;
import com.haulmont.cuba.core.app.UniqueNumbersService;
import com.haulmont.cuba.core.global.DataManager;
import com.haulmont.cuba.core.global.LoadContext;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.core.global.View;
import com.haulmont.taskman.entity.MessageDirection;
import com.haulmont.taskman.entity.Task;
import com.haulmont.taskman.entity.TaskMessage;
import com.haulmont.taskman.entity.TaskState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.transaction.Transactional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component(ImapMessageBean.NAME)
public class ImapMessageBean {
    public static final String NAME = "imap-addon-tutorial-demo_ImapMessageBean";

    private static final Logger log = LoggerFactory.getLogger(ImapMessageBean.class);

    protected final static Pattern TASK_NUMBER_PATTERN = Pattern.compile("^(?:Re:\\s*)*#(\\d+)\\s");

    @Inject
    private DataManager dataManager;

    @Inject
    private ImapAPI imapAPI;

    @Inject
    private Metadata metadata;

    @Inject
    private HtmlToTextConverterService htmlToText;

    @Inject
    private UniqueNumbersService uniqueNumbersAPI;

    @Inject
    private ResponseEmailAsyncSenderBean emailSender;

    @EventListener
    @Transactional
    public void handleMessage(NewEmailImapEvent imapEvent) {
        ImapMessage imapMessage = imapEvent.getMessage();
        log.info(String.format("Process email subject = '%s', id = %d", imapMessage.getCaption(), imapEvent.getMessageId()));

        if (isTaskMessageExistsForImapMessage(imapMessage)) {
            log.info(String.format("the message has already been processed, id = %s", imapMessage.getMessageId()));
            return;
        }

        ImapMessageDto imapMessageDto = imapAPI.fetchMessage(imapMessage);
        String subject = imapMessageDto.getSubject();

        TaskMessage message = buildNewTaskMessage(imapMessage, imapMessageDto);

        boolean isNewTask = false;

        Task task = retrieveTaskByEmailSubject(subject);
        if (task == null) {
            task = buildNewTask(message);
            isNewTask = true;
        }

        message.setTask(task);

        dataManager.commit(task, message);

        if (isNewTask) {
            emailSender.sendNewTaskResponse(task, message, imapMessageDto);
        } else {
            emailSender.sendUpdateTaskResponse(task, message, imapMessageDto);
        }
    }

    private Task buildNewTask(TaskMessage message) {
        Long taskNumber = uniqueNumbersAPI.getNextNumber(Task.class.getSimpleName());

        Task task = metadata.create(Task.class);
        task.setState(TaskState.OPEN);
        task.setContent(message.getContent());
        task.setSubject(String.format("#%d %s", taskNumber, message.getSubject()));
        task.setReporterEmail(message.getReporter());
        task.setNumber(taskNumber);

        return task;
    }

    @Nullable
    private Task retrieveTaskByEmailSubject(String subject) {
        Matcher matcher = TASK_NUMBER_PATTERN.matcher(subject);
        Task task = null;

        if (matcher.find()) {
            String taskNumber = matcher.group(1);
            task = findTaskByNumber(Integer.valueOf(taskNumber));
            if (task != null && TaskState.REPLIED == task.getState()) {
                task.setState(TaskState.ASSIGNED);
            }
        }

        return task;
    }

    private Task findTaskByNumber(Integer taskNumber) {
        return dataManager.load(Task.class)
                .view("task-view")
                .query("select t from taskman_Task t where t.number = :number")
                .parameter("number", taskNumber)
                .optional()
                .orElse(null);
    }

    private TaskMessage buildNewTaskMessage(ImapMessage imapMessage, ImapMessageDto imapMessageDto) {
        String messageText = htmlToText.convert(imapMessageDto.getBody());

        TaskMessage message = metadata.create(TaskMessage.class);
        message.setContent(messageText);
        message.setSubject(imapMessageDto.getSubject());
        message.setReporter(imapMessageDto.getFrom());
        message.setImapMessage(imapMessage);
        message.setDirection(MessageDirection.INBOX);
        message.setOriginalImapMessageId(imapMessage.getMessageId());

        return message;
    }

    private boolean isTaskMessageExistsForImapMessage(ImapMessage imapMessage) {
        LoadContext<TaskMessage> loadContext = LoadContext.create(TaskMessage.class)
                .setQuery(
                        LoadContext.createQuery("select tm from taskman_TaskMessage tm where tm.originalImapMessageId = :imapMessageId")
                                .setParameter("imapMessageId", imapMessage.getMessageId()))
                .setView(View.MINIMAL);
        return dataManager.getCount(loadContext) > 0;
    }

    public void handleInboxMessage(NewEmailImapEvent imapEvent) {
        ImapMessage message = imapEvent.getMessage();
        log.info("INBOX folder event: subj = '%s'", message.getCaption());
    }
}