package rs.ac.soas.discovery;

@org.springframework.boot.autoconfigure.SpringBootApplication
@org.springframework.cloud.netflix.eureka.server.EnableEurekaServer
public class Application {
  public static void main(String[] args) {
    org.springframework.boot.SpringApplication.run(Application.class, args);
  }
}
