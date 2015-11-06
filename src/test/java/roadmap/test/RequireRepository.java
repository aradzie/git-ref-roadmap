package roadmap.test;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface RequireRepository {
    /** @return Repository initializer class. */
    Class value() default RepositoryRule.Setup.Empty.class;
}
