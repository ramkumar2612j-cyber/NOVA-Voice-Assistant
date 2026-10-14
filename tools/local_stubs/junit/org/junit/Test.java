package org.junit;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
public @interface Test { Class<? extends Throwable> expected() default None.class; long timeout() default 0L; class None extends Throwable {} }
