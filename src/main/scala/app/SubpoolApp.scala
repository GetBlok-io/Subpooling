package app

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
@OpenAPIDefinition
class SubpoolApp {

}
object SubpoolApp {
  def main(args: Array[String]): Unit = SpringApplication.run(classOf[SubpoolApp], args:_*)
}
