package space.npstr.sqlsauce.fp.types;

import space.npstr.sqlsauce.entities.SaucedEntity;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.function.Function;

/**
 * Created by napster on 03.12.17.
 * <p>
 * Describes a transformation that can be applied to an entity.
 * <p>
 * Somewhat of a Type class.
 * <p>
 * The relation between I and E is controlled by the publicly available constructors / factory methods.
 * <p>
 * As for the name....I needed a name ¯\_(ツ)_/¯. Didnt want another "EntitySomething" class.
 */
public class Transfiguration<I, E> {

    public final EntityKey<I, E> key;
    public final Function<E, E> tf;


    @Nonnull
    public static <E extends SaucedEntity<I, E>, I extends Serializable>
    Transfiguration<I, E> of(@Nonnull final EntityKey<I, E> entityKey,
                             @Nonnull final Function<E, E> transformation) {
        return new Transfiguration<>(entityKey, transformation);
    }

    private Transfiguration(@Nonnull final EntityKey<I, E> entityKey, @Nonnull final Function<E, E> transformation) {
        this.key = entityKey;
        this.tf = transformation;
    }
}
