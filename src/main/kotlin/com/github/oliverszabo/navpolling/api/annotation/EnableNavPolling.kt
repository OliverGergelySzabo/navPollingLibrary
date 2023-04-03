package com.github.oliverszabo.navpolling.api.annotation

import com.github.oliverszabo.navpolling.config.LibrarySpringConfig
import org.springframework.context.annotation.Import

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@Import(LibrarySpringConfig::class)
annotation class EnableNavPolling