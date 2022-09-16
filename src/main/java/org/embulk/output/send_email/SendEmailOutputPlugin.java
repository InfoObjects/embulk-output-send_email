package org.embulk.output.send_email;


import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.embulk.config.*;
import org.embulk.spi.*;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class SendEmailOutputPlugin
        implements OutputPlugin {
    public interface PluginTask
            extends Task {

        @Config("filetype")
        @ConfigDefault("\"json\"")
        public String getFileType();

        @Config("to")
        public String getTO();

        @Config("from")
        public String getFrom();

        @Config("password")
        public String getPassword();

        @Config("port")
        public String getPort();

        @Config("host")
        public String getHost();

        @Config("row")
        @ConfigDefault("-1")
        public int getRow();

    }


    @Override
    public ConfigDiff transaction(ConfigSource config,
                                  Schema schema, int taskCount,
                                  OutputPlugin.Control control) {
        PluginTask task = config.loadConfig(PluginTask.class);
        control.run(task.dump());
        return Exec.newConfigDiff();
    }

    @Override
    public ConfigDiff resume(TaskSource taskSource,
                             Schema schema, int taskCount,
                             OutputPlugin.Control control) {
        throw new UnsupportedOperationException("myp output plugin does not support resuming");
    }



    @Override
    public void cleanup(TaskSource taskSource,
                        Schema schema, int taskCount,
                        List<TaskReport> successTaskReports) {
    }

    @Override
    public TransactionalPageOutput open(TaskSource taskSource, Schema schema, int taskIndex) {
        PageReader pageReader;
        PluginTask task = taskSource.loadTask(PluginTask.class);
        pageReader = new PageReader(schema);
        return new PageTransactionalOutput(task, pageReader, schema);
    }

    public static class PageTransactionalOutput implements TransactionalPageOutput {
        PluginTask task;
        PageReader pageReader;
        Schema schema;

        public PageTransactionalOutput(PluginTask task, PageReader pageReader, Schema schema) {
            this.task = task;
            this.pageReader = pageReader;
            this.schema = schema;
        }

        @Override
        public void add(Page page) {
            ArrayList<LinkedHashMap<String, Object>> mapList = new ArrayList<>();

            pageReader.setPage(page);
            int rows = task.getRow();
            if (task.getRow() == -1) {
                rows = Integer.MAX_VALUE;
            }

            readingAndSettingDataToMap(pageReader, rows, mapList);
            pageReader.close();
            try {
                Transport.send(prepareMessage(getSession(task), task.getFrom(), task.getTO(), mapList, schema, task));
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        private void readingAndSettingDataToMap(PageReader pageReader, int rows, ArrayList<LinkedHashMap<String, Object>> mapList) {
            while (pageReader.nextRecord() && rows-- > 0) {
                LinkedHashMap<String, Object> map = new LinkedHashMap<>();

                pageReader.getSchema().visitColumns(new ColumnVisitor() {
                    @Override
                    public void booleanColumn(Column column) {
                        if (pageReader.isNull(column)) {
                            map.put(column.getName(), "");
                        } else {
                            map.put(column.getName(), pageReader.getBoolean(column));
                        }
                    }

                    @Override
                    public void longColumn(Column column) {
                        if (pageReader.isNull(column)) {
                            map.put(column.getName(), "");
                        } else {
                            map.put(column.getName(), pageReader.getLong(column));
                        }
                    }

                    @Override
                    public void doubleColumn(Column column) {
                        if (pageReader.isNull(column)) {
                            map.put(column.getName(), "");
                        } else {
                            map.put(column.getName(), pageReader.getDouble(column));
                        }

                    }

                    @Override
                    public void stringColumn(Column column) {
                        if (pageReader.isNull(column)) {
                            map.put(column.getName(), "");
                        } else {
                            map.put(column.getName(), pageReader.getString(column));
                        }
                    }

                    @Override
                    public void timestampColumn(Column column) {
                        if (pageReader.isNull(column)) {
                            map.put(column.getName(), "");
                        } else {
                            map.put(column.getName(), pageReader.getTimestamp(column));
                        }

                    }

                    @Override
                    public void jsonColumn(Column column) {
                        if (pageReader.isNull(column)) {
                            map.put(column.getName(), "");
                        } else {
                            map.put(column.getName(), pageReader.getJson(column));
                        }

                    }
                });
                mapList.add(map);
            }
        }

        private Session getSession(PluginTask task) {
            try {
                Properties properties = new Properties();
                properties.put("mail.smtp.starttls.enable", "true");
                properties.put("mail.smtp.ssl.protocols", "TLSv1.2");
                properties.put("mail.smtp.host", task.getHost());
                properties.put("mail.smtp.port", task.getPort());
                properties.put("mail.smtp.auth", "true");

                return Session.getInstance(properties, new javax.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(task.getFrom(), task.getPassword());
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        }

        private static Message prepareMessage(Session session, String myAccountEmail, String recepeint, ArrayList<LinkedHashMap<String, Object>> mapList, Schema schema, PluginTask task) {
            try {
                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(myAccountEmail));
                message.setRecipient(Message.RecipientType.TO, new InternetAddress(recepeint));
                message.setSubject("Message from email plugin.");

                if (task.getFileType().equalsIgnoreCase("html")) {
                    setHtmlContent(message, mapList, schema);
                } else if (task.getFileType().equalsIgnoreCase("json")) {
                    setJsonContent(message, mapList, schema);
                }
                return message;
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        private static void setHtmlContent(Message message, ArrayList<LinkedHashMap<String, Object>> mapList, Schema schema) {

            try {
                MimeMultipart multipart = new MimeMultipart();
                BodyPart messageBodyPart = new MimeBodyPart();
                StringBuilder email = new StringBuilder();

                email.append("<!DOCTYPE html><html><body>"
                        + "<table style='border:1px solid #96D4D4;border-collapse: collapse;width: 100%'>");

                email.append("<tr>");
                for (int i = 0; i < schema.size(); i++) {
                    email.append("<th style='border:2px solid black; border-collapse: collapse;border-color: #96D4D4'>");
                    email.append(schema.getColumn(i).getName().split("/")[0]);
                    email.append("</th>");
                }
                email.append("</tr>");

                for (int i = 0; i < mapList.size(); i++) {
                    Map<String, Object> map = mapList.get(i);
                    email.append("<tr >");
                    for (Map.Entry<String, Object> entry : map.entrySet()) {
                        email.append("<td style='border:1px solid black;border-collapse: collapse;text-align:center;border-color: #96D4D4'>");
                        email.append(entry.getValue());
                        email.append("</td>");
                    }
                    email.append("<tr>");
                }
                email.append("</table></body></html>");

                messageBodyPart.setContent(email.toString(), "text/html");
                multipart.addBodyPart(messageBodyPart);
                message.setContent(multipart);
            } catch (Exception e) {
                System.out.println("Not able to send data");
                throw new RuntimeException(e);
            }

        }

        private static void setJsonContent(Message message, ArrayList<LinkedHashMap<String, Object>> mapList, Schema schema) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                String value = null;
                value = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapList);
                message.setText(value);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }

        }

        @Override
        public void finish() {

        }

        @Override
        public void close() {

        }

        @Override
        public void abort() {

        }

        @Override
        public TaskReport commit() {
            return null;
        }
    }
}