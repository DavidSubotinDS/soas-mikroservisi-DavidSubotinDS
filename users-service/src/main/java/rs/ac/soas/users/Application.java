package rs.ac.soas.users;

@org.springframework.boot.autoconfigure.SpringBootApplication
@org.springframework.cloud.openfeign.EnableFeignClients(basePackages = "rs.ac.soas.contract")
@org.springframework.context.annotation.Import(rs.ac.soas.util.ServiceConfiguration.class)
public class Application {
  public static void main(String[] args) {
    org.springframework.boot.SpringApplication.run(Application.class, args);
  }
}
