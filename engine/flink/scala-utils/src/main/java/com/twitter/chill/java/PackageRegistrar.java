package com.twitter.chill.java;

import com.twitter.chill.IKryoRegistrar;
/**
 * Creates a registrar for all the serializers in the chill.java package
 */
public class PackageRegistrar {

    static public IKryoRegistrar all() {
        return new IterableRegistrar(

        );
    }
}