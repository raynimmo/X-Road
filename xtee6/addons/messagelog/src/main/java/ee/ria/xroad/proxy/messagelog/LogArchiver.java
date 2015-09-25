package ee.ria.xroad.proxy.messagelog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import akka.actor.UntypedActor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.hibernate.Session;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.messagelog.LogRecord;
import ee.ria.xroad.common.messagelog.MessageRecord;
import ee.ria.xroad.common.messagelog.TimestampRecord;
import ee.ria.xroad.common.messagelog.archive.DigestEntry;
import ee.ria.xroad.common.messagelog.archive.LogArchiveBase;
import ee.ria.xroad.common.messagelog.archive.LogArchiveWriter;

import static ee.ria.xroad.common.messagelog.MessageLogProperties.getArchiveTransferCommand;
import static ee.ria.xroad.proxy.messagelog.MessageLogDatabaseCtx.doInTransaction;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * Reads all non-archived time-stamped records from the database, writes them
 * to archive file and marks the records as archived.
 */
@Slf4j
@RequiredArgsConstructor
public class LogArchiver extends UntypedActor {

    private static final int MAX_RECORDS_IN_ARCHIVE = 10;

    public static final String START_ARCHIVING = "doArchive";

    private final Path archivePath;
    private final Path workingPath;

    @Override
    public void onReceive(Object message) throws Exception {
        log.trace("onReceive({})", message);

        if (message.equals(START_ARCHIVING)) {
            try {
                handleArchive();
            } catch (Throwable t) {
                log.error("Failed to archive log records", t);
            }
        } else {
            unhandled(message);
        }
    }

    private void handleArchive() throws Exception {
        doInTransaction(session -> {
            List<LogRecord> records = getRecordsToBeArchived(session);
            if (records == null || records.isEmpty()) {
                log.info("No records to be archived at this time");
                return null;
            }

            log.info("Archiving log records - start");

            long start = System.currentTimeMillis();
            int recordsArchived = 0;

            try (LogArchiveWriter archiveWriter = createLogArchiveWriter(session)) {
                while (!records.isEmpty()) {
                    doArchive(archiveWriter, records);
                    runTransferCommand(getArchiveTransferCommand());
                    recordsArchived += records.size();

                    //flush changes (records marked as archived) and free memory
                    //used up by cached records retrieved previously in the session
                    session.flush();
                    session.clear();

                    records = getRecordsToBeArchived(session);
                }
            } catch (Exception e) {
                throw new CodedException(ErrorCodes.X_INTERNAL_ERROR, e);
            }

            log.info("Archived {} log records in {} ms", recordsArchived,
                    System.currentTimeMillis() - start);

            return null;
        });
    }

    private void doArchive(
            LogArchiveWriter archiveWriter, List<LogRecord> records)
            throws Exception {
        for (LogRecord record : records) {
            archiveWriter.write(record);
        }
    }

    private LogArchiveWriter createLogArchiveWriter(Session session) {
        return new LogArchiveWriter(
            getArchivePath(),
            getWorkingPath(),
            this.new HibernateLogArchiveBase(session)
        );
    }

    private Path getArchivePath() {
        if (!Files.isDirectory(archivePath)) {
            throw new RuntimeException(
                    "Log output path (" + archivePath + ") must be directory");
        }

        if (!Files.isWritable(archivePath)) {
            throw new RuntimeException(
                    "Log output path (" + archivePath + ") must be writable");
        }

        return archivePath;
    }

    private Path getWorkingPath() {
        if (!Files.isDirectory(workingPath)) {
            throw new RuntimeException(
                    "Log working path (" + workingPath + ") must be directory");
        }

        if (!Files.isWritable(workingPath)) {
            throw new RuntimeException(
                    "Log working path (" + workingPath + ") must be writable");
        }

        return workingPath;
    }

    protected List<LogRecord> getRecordsToBeArchived(Session session) {
        List<LogRecord> recordsToArchive = new ArrayList<>();

        int allowedInArchiveCount = MAX_RECORDS_IN_ARCHIVE;
        for (TimestampRecord ts : getNonArchivedTimestampRecords(session)) {
            List<MessageRecord> messages =
                    getNonArchivedMessageRecords(session, ts.getId(),
                            allowedInArchiveCount);
            if (allTimestampMessagesArchived(session, ts.getId())) {
                log.trace("Timestamp record #{} will be archived",
                        ts.getId());
                recordsToArchive.add(ts);
            } else {
                log.trace("Timestamp record #{} still related to"
                                + " non-archived message records",
                        ts.getId());
            }

            recordsToArchive.addAll(messages);
            allowedInArchiveCount -= messages.size();
            if (allowedInArchiveCount <= 0) {
                break;
            }
        }
        return recordsToArchive;
    }

    @SuppressWarnings("unchecked")
    protected List<TimestampRecord> getNonArchivedTimestampRecords(
            Session session) {
        return session
                .createCriteria(TimestampRecord.class)
                .add(Restrictions.eq("archived", false))
                .list();
    }

    @SuppressWarnings("unchecked")
    protected List<MessageRecord> getNonArchivedMessageRecords(Session session,
            Long timestampRecordNumber, int maxRecordsToGet) {
        return session
                .createCriteria(MessageRecord.class)
                .add(Restrictions.eq("archived", false))
                .add(Restrictions.eq("timestampRecord.id",
                        timestampRecordNumber))
                .setMaxResults(maxRecordsToGet)
                .list();
    }

    protected boolean allTimestampMessagesArchived(Session session,
            Long timestampRecordNumber) {
        Long result = (Long) session
                .createCriteria(MessageRecord.class)
                .add(Restrictions.eq("archived", false))
                .add(Restrictions.eq("timestampRecord.id",
                        timestampRecordNumber))
                .setProjection(Projections.rowCount())
                .uniqueResult();
        return result == 0;
    }


    // TODO The method is in this class as protected to facilitate testing.
    // TODO Is there any better way to recognize invocation of this method?
    protected void markArchiveCreated(
            final DigestEntry lastArchive, final Session session)
            throws Exception {
        if (lastArchive == null) {
            return;
        }

        log.debug("Digest entry will be saved here...");

        session.createQuery(getLastEntryDeleteQuery()).executeUpdate();
        session.save(lastArchive);
    }

    private String getLastEntryDeleteQuery() {
        return "delete from " + DigestEntry.class.getName();
    }

    private static void runTransferCommand(String transferCommand) {
        if (isBlank(transferCommand)) {
            return;
        }

        log.info("Transferring archives with shell command: \t{}",
                transferCommand);

        try {
            String[] command = new String[] {"/bin/bash", "-c", transferCommand};

            Process process = new ProcessBuilder(command).start();

            StandardErrorCollector standardErrorCollector =
                    new StandardErrorCollector(process);

            new StandardOutputReader(process).start();
            standardErrorCollector.start();

            standardErrorCollector.join();
            process.waitFor();

            int exitCode = process.exitValue();

            if (exitCode != 0) {
                String errorMsg = String.format(
                        "Running archive transfer command '%s' "
                        + "exited with status '%d'",
                        transferCommand,
                        exitCode);

                log.error(
                        "{}\n -- STANDARD ERROR START\n{}\n"
                        + " -- STANDARD ERROR END",
                        errorMsg,
                        standardErrorCollector.getStandardError());
            }
        } catch (Exception e) {
            log.error(
                    "Failed to execute archive transfer command '{}'",
                    transferCommand);
        }
    }

    @Value
    private class HibernateLogArchiveBase implements LogArchiveBase {

        private Session session;

        @Override
        public void markArchiveCreated(DigestEntry lastArchive)
                throws Exception {
            LogArchiver.this.markArchiveCreated(lastArchive, session);
        }

        @Override
        public void markRecordArchived(LogRecord logRecord) throws Exception {
            log.trace("Setting {} #{} archived",
                    logRecord.getClass().getName(), logRecord.getId());

            logRecord.setArchived(true);
            session.saveOrUpdate(logRecord);
        }

        @Override
        @SuppressWarnings("unchecked")
        public DigestEntry loadLastArchive() throws Exception {
            List<DigestEntry> lastArchiveEntries = session
                    .createQuery(getLastArchiveDigestQuery())
                    .setMaxResults(1).list();

            return lastArchiveEntries.isEmpty()
                    ? DigestEntry.empty() : lastArchiveEntries.get(0);
        }

        private String getLastArchiveDigestQuery() {
            return "select new " + DigestEntry.class.getName()
                    + "(d.digest, d.fileName) from DigestEntry d";
        }
    }

    @RequiredArgsConstructor
    private static class StandardOutputReader extends Thread {
        private final Process process;

        @Override
        public void run() {
            try (InputStream input = process.getInputStream()) {
                IOUtils.copy(input, new NullOutputStream());
            } catch (IOException e) {
                // We can ignore it.
                log.error("Could not read standard output", e);
            }
        }
    }

    @RequiredArgsConstructor
    private static class StandardErrorCollector extends Thread {
        private final Process process;

        @Getter
        private String standardError;

        @Override
        public void run() {
            try (InputStream error = process.getErrorStream()) {
                standardError = IOUtils.toString(error, StandardCharsets.UTF_8);
            } catch (IOException e) {
                // We can ignore it.
                log.error("Could not read standard error", e);
            }
        }
    }
}
