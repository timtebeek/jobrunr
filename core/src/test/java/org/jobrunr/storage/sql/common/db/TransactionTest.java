package org.jobrunr.storage.sql.common.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.SQLException;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionTest {

    @Mock
    private Connection connection;

    @Test
    void transactionSucceedsWithAutocommitTrue() throws SQLException {
        //GIVEN
        when(connection.getAutoCommit()).thenReturn(true, false);
        final Transaction transaction = new Transaction(connection);

        //WHEN
        transaction.commit();
        transaction.close();

        //THEN
        verify(connection).commit();
        verify(connection, never()).rollback();
        verify(connection).setAutoCommit(true);
    }

    @Test
    void transactionSucceedsWithAutocommitFalse() throws SQLException {
        //GIVEN
        when(connection.getAutoCommit()).thenReturn(false);
        final Transaction transaction = new Transaction(connection);

        //WHEN
        transaction.commit();
        transaction.close();

        //THEN
        verify(connection).commit();
        verify(connection, never()).rollback();
        verify(connection, never()).setAutoCommit(anyBoolean());
    }

    @Test
    void transactionFailsWithAutocommitTrue() throws SQLException {
        //GIVEN
        when(connection.getAutoCommit()).thenReturn(true, false);
        final Transaction transaction = new Transaction(connection);

        //WHEN
        transaction.close();

        //THEN
        verify(connection, never()).commit();
        verify(connection).rollback();
        verify(connection).setAutoCommit(true);
    }

    @Test
    void transactionFailsWithAutocommitFalse() throws SQLException {
        //GIVEN
        when(connection.getAutoCommit()).thenReturn(false);
        final Transaction transaction = new Transaction(connection);

        //WHEN
        transaction.close();

        //THEN
        verify(connection, never()).commit();
        verify(connection).rollback();
        verify(connection, never()).setAutoCommit(anyBoolean());
    }
}