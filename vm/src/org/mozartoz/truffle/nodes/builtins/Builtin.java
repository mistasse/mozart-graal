package org.mozartoz.truffle.nodes.builtins;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Builtin {

	public static final Builtin DEFAULT = new Builtin() {

		private final int[] EMPTY_INT_ARRAY = new int[0];

		@Override
		public Class<? extends Annotation> annotationType() {
			return Builtin.class;
		}

		@Override
		public String name() {
			return "";
		}

		public boolean proc() {
			return false;
		}

		@Override
		public int[] deref() {
			return EMPTY_INT_ARRAY;
		}

		@Override
		public int[] tryDeref() {
			return EMPTY_INT_ARRAY;
		};
	};

	String name() default "";

	boolean proc() default false;

	int[] deref() default {};

	int[] tryDeref() default {};

	public static final int ALL = -1;

}
