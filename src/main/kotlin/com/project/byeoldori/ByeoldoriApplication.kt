package com.project.byeoldori

import io.github.cdimascio.dotenv.Dotenv
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ByeoldoriApplication

fun main(args: Array<String>) {
	val dotenv = Dotenv.load() // 환경변수 자동설정
	dotenv.entries().forEach { entry ->
		System.setProperty(entry.key, entry.value)
	}

	runApplication<ByeoldoriApplication>(*args)
}
