package com.project.byeoldori

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<ByeoldoriApplication>().with(TestcontainersConfiguration::class).run(*args)
}
