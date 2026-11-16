package com.observernotes.ui.assets;

/**
 * Como uma imagem é esticada sem deformar as pontas arredondadas.
 *
 * @param capLeft   largura (px da imagem original) da ponta esquerda, nunca esticada
 * @param capRight  largura da ponta direita
 * @param tileWidth 0 = o miolo é esticado; &gt; 0 = o miolo é repetido a partir de
 *                  {@code capLeft} usando uma faixa desta largura (para preservar tracejados)
 */
public record SliceSpec(int capLeft, int capRight, int tileWidth) {

    public static SliceSpec stretch(int capLeft, int capRight) {
        return new SliceSpec(capLeft, capRight, 0);
    }

    public static SliceSpec tiled(int capLeft, int capRight, int tileWidth) {
        return new SliceSpec(capLeft, capRight, tileWidth);
    }
}
