package zelda.facade;

public enum direction {
    HAUT("z"),
    BAS("s"),
    GAUCHE("q"),
    DROITE("d");

    public final String label;

    private direction(String label) {
        this.label = label;
    }
}
