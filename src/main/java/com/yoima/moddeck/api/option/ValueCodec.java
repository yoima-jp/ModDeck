package com.yoima.moddeck.api.option;

/** Converts custom selector/list values to storage-neutral strings. */
public interface ValueCodec<T> {
    String encode(T value);
    T decode(String value);
}
