package com.razorpayhackthon.revenue_recovery.auth;

import com.razorpayhackthon.revenue_recovery.enums.DeskRole;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
	DeskRole[] value();
}
