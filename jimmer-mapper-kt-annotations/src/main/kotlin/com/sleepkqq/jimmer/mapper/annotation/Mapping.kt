package com.sleepkqq.jimmer.mapper.annotation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
annotation class Mapping(
	val source: String,
	val target: String,
)
