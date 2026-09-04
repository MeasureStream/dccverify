package it.polito.dccverify

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DccverifyApplication

fun main(args: Array<String>) {
	runApplication<DccverifyApplication>(*args)
}
