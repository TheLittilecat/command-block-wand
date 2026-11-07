package command.wand;

public enum SkillType {
    SINGLE("脉冲"),
    PULSE("连锁"),
    REPEAT("循环");

    public final String label;

    SkillType(String label) {
        this.label = label;
    }

    public SkillType next() {
        return switch (this) {
            case SINGLE -> PULSE;
            case PULSE -> REPEAT;
            case REPEAT -> SINGLE;
        };
    }
}
