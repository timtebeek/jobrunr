package org.jobrunr.server.tasks.zookeeper;

import org.jobrunr.server.tasks.AbstractTaskTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.jobrunr.jobs.states.StateName.DELETED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

class DeleteDeletedJobsPermanentlyTaskTest extends AbstractTaskTest {

    DeleteDeletedJobsPermanentlyTask task;

    @BeforeEach
    void setUpTask() {
        task = new DeleteDeletedJobsPermanentlyTask(backgroundJobServer);
    }

    @Test
    void task() {
        runTask(task);

        verify(storageProvider).deleteJobsPermanently(eq(DELETED), any());
    }
}