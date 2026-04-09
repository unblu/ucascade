package controller.model;

public enum SquashOption {
	DEFAULT,
	TRUE,
	FALSE;

	public Boolean toBoolean() {
		return switch (this) {
			case DEFAULT -> null;
			case TRUE -> true;
			case FALSE -> false;
		};
	}
}
