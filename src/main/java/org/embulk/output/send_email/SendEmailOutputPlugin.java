package org.embulk.output.send_email;


import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.embulk.config.*;
import org.embulk.spi.*;
import org.jsoup.Jsoup;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class SendEmailOutputPlugin
        implements OutputPlugin {
    public interface PluginTask
            extends Task {

        @Config("format_type")
        @ConfigDefault("\"json\"")
        public String getFormatType();

        @Config("to")
        public List<String> getTO();

        @Config("cc")
        @ConfigDefault("[]")
        public List<String> getCC();

        @Config("from")
        public String getFrom();

        @Config("password")
        @ConfigDefault("\"password\"")
        public String getPassword();

        @Config("port")
        @ConfigDefault("\"25\"")
        public String getPort();

        @Config("subject")
        public String getSubject();

        @Config("auth")
        @ConfigDefault("false")
        public boolean getAuth();

        @Config("host")
        @ConfigDefault("\"smtp.gmail.com\"")
        public String getHost();

        @Config("protocol")
        @ConfigDefault("\"TLSv1.2\"")
        public String getProtocol();

        @Config("row")
        @ConfigDefault("-1")
        public int getRow();

        @Config("username")
        @ConfigDefault("\"\"")
        public String getUserName();

        @Config("template")
        @ConfigDefault("\"\"")
        public String getEmailTemplate();

        @Config("is_html")
        @ConfigDefault("false")
        public boolean getIsHTML();

        @Config("enable_starttls")
        @ConfigDefault("\"true\"")
        public String getEnableStarttls();
    }

    @Override
    public ConfigDiff transaction(ConfigSource config,
                                  Schema schema, int taskCount,
                                  OutputPlugin.Control control) {
        PluginTask task = config.loadConfig(PluginTask.class);
        System.out.println(task);
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
                Transport.send(prepareMessage(getSession(task), task.getFrom(), task.getTO(), task.getCC(), mapList, schema, task));
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
                properties.put("mail.smtp.auth", task.getAuth());
                properties.put("mail.smtp.starttls.enable", task.getEnableStarttls());

                properties.put("mail.smtp.ssl.protocols", task.getProtocol());
                properties.put("mail.smtp.host", task.getHost());
                properties.put("mail.smtp.port", task.getPort());

                if(task.getAuth()) {

                    return Session.getInstance(properties, new javax.mail.Authenticator() {
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(task.getFrom(), task.getPassword());
                        }
                    });
                }else{
                    properties.put("mail.smtp.user", task.getUserName());
                    return Session.getDefaultInstance(properties);
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        }

        private static Message prepareMessage(Session session, String from, List<String> to, List<String> cc, ArrayList<LinkedHashMap<String, Object>> mapList, Schema schema, PluginTask task) {
            try {
                String listStringCC=null;
                String listStringTo = to.stream().map(Object::toString)
                        .collect(Collectors.joining(","));
                if(!(cc.size() ==0)) {
                     listStringCC = cc.stream().map(Object::toString)
                            .collect(Collectors.joining(","));
                }
                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(from));
                if(listStringCC!=null) {
                    message.addRecipients(Message.RecipientType.CC, InternetAddress.parse(listStringCC));
                }
                message.addRecipients(Message.RecipientType.TO, InternetAddress.parse(listStringTo));
                message.setSubject(task.getSubject());
                if(task.getEmailTemplate().length()!=0){
                    if(task.getIsHTML()) {
                        setEmailTemplate(message, mapList, schema, task);
                    }else{
                        setTextContent(message,mapList,schema,task);
                    }
                }else{
                    if (task.getFormatType().equalsIgnoreCase("html")) {
                        setHtmlContent(message,mapList,schema);
                    } else if (task.getFormatType().equalsIgnoreCase("json")) {
                        setJsonContent(message, mapList, schema);
                    }
                }
                return message;
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        private static void setEmailTemplate(Message message, ArrayList<LinkedHashMap<String, Object>> mapList, Schema schema,PluginTask task) {

            try {
                MimeMultipart multipart = new MimeMultipart();
                BodyPart messageBodyPart = new MimeBodyPart();
                StringBuilder email = new StringBuilder();
                StringBuilder email1= new StringBuilder();

                email.append("<table style='border:1px solid #96D4D4;border-collapse: collapse;width: 100%'>");
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
                email.append("</table>");
                email.append("<br>");
                String s=null;
                try {
                    File sourceFile = new File(task.getEmailTemplate());
                    org.jsoup.nodes.Document doc = Jsoup.parse(sourceFile, "UTF-8");
                    org.jsoup.nodes.Element elements = doc.body();
                    s=StringUtils.replace(String.valueOf(elements),"{{data}}",email.toString());

                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                email1.append(s);
                messageBodyPart.setContent(email1.toString(), "text/html");
                multipart.addBodyPart(messageBodyPart);
                message.setContent(multipart);
            } catch (Exception e) {
                System.out.println("Not able to send data");
                throw new RuntimeException(e);
            }

        }
        private static void setTextContent(Message message, ArrayList<LinkedHashMap<String, Object>> mapList, Schema schema,PluginTask task) {

            try {
                MimeMultipart multipart = new MimeMultipart();
                BodyPart messageBodyPart = new MimeBodyPart();
                StringBuilder email = new StringBuilder();

                StringBuilder email1 = new StringBuilder();

                email.append("\n");
                email.append("\n");
                for (int i = 0; i < schema.size(); i++) {
                    email.append(schema.getColumn(i).getName().split("/")[0]);
                    email.append(",");
                }
                email.append("\n");

                for (int i = 0; i < mapList.size(); i++) {
                    Map<String, Object> map = mapList.get(i);
                    email.append("\n");
                    for (Map.Entry<String, Object> entry : map.entrySet()) {
                        email.append(entry.getValue());
                        email.append(",");
                    }
                    email.append("\n");
                }

                String s=null;
                try {
                    File sourceFile = new File(task.getEmailTemplate());
                    org.jsoup.nodes.Document doc = Jsoup.parse(sourceFile, "UTF-8");
                    String elements = doc.text();
                    s=StringUtils.replace(String.valueOf(elements),"{{data}}",email.toString());

                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
               // email1.append("\n");
                email1.append(s);
                email1.append("\n");
                messageBodyPart.setText(email1.toString());
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

        private static void setHtmlContent(Message message, ArrayList<LinkedHashMap<String, Object>> mapList, Schema schema) {

            try {
                MimeMultipart multipart = new MimeMultipart();
                BodyPart messageBodyPart = new MimeBodyPart();
                StringBuilder email = new StringBuilder();

                email.append("<!DOCTYPE html><html><body>");
                email.append("<p>Hello Team,</p>");
                email.append("<header>\n" +
                        "    <h3>This is the daily ETL Report</h3>\n" +
                        "</header>");

                email.append("<table style='border:1px solid #96D4D4;border-collapse: collapse;width: 100%'>");

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
                email.append("</table>");
                email.append("<footer>\n" +
                        "  <p>Thanks</p>\n" +
                        "  <p>By ETL process</p>\n" +
                        "</footer>");
                email.append("</body></html>");

                messageBodyPart.setContent(email.toString(), "text/html");
                multipart.addBodyPart(messageBodyPart);
                message.setContent(multipart);
            } catch (Exception e) {
                System.out.println("Not able to send data");
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
