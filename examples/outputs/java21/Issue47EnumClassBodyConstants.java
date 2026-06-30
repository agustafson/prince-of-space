package p;

enum E {
    SIMPLE,
    OVERRIDE {
        void run() {
            return;
        }
    },
    WITH_ARGS(1) {
        void run() {
            return;
        }
    };
    private final int code;
    E() {
        this(0);
    }
    E(int code) {
        this.code = code;
    }
    void base() {
        return;
    }
}
