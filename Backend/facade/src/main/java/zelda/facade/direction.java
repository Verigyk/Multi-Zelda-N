package zelda.facade;

public enum direction {
    HAUT("z", new int[]{0, -1}),
    BAS("s", new int[]{0, 1}),
    GAUCHE("q", new int[]{1, 0}),
    DROITE("d", new int[]{-1, 0});

    public final String label;
    public final int[] translated_vector;

    private direction(String label, int[] translated_vector) {
        this.label = label;
        this.translated_vector = translated_vector;
    }
}
