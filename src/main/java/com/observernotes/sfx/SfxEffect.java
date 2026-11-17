package com.observernotes.sfx;

/** Efeitos curtos incorporados ao aplicativo. */
public enum SfxEffect {
    CLICK("/assets/sfx/click.mp3"),
    PAGE("/assets/sfx/page.mp3"),
    CREATE("/assets/sfx/create.mp3"),
    COMPLETE("/assets/sfx/complete.mp3"),
    MEDAL("/assets/sfx/medal.mp3");

    private final String resource;

    SfxEffect(String resource) {
        this.resource = resource;
    }

    public String resource() {
        return resource;
    }
}
