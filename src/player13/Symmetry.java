package player13;

public enum Symmetry {
    ROTATIONAL(0),
    HORIZONTAL(1),
    VERTICAL(2);
    Symmetry(int idArg) {
        this.id = idArg;
    }
    int id;
}
