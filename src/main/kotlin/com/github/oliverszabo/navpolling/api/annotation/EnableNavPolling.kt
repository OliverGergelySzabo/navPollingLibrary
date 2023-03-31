package com.github.oliverszabo.navpolling.api.annotation

import com.github.oliverszabo.navpolling.config.NavPollingLibraryConfig
import org.springframework.context.annotation.Import

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@Import(NavPollingLibraryConfig::class)
annotation class EnableNavPolling