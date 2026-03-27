package com.sleepkqq.jimmer.mapper.annotation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class IgnoreMapping(vararg val value: String)
