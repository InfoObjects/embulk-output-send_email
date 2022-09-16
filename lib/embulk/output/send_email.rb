Embulk::JavaPlugin.register_output(
  "send_email", "org.embulk.output.send_email.SendEmailOutputPlugin",
  File.expand_path('../../../../classpath', __FILE__))
