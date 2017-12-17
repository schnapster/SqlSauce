package space.npstr.sqlsauce.fp.types;

import space.npstr.sqlsauce.entities.IEntity;
import space.npstr.sqlsauce.entities.SaucedEntity;

import javax.annotation.Nonnull;
import java.io.Serializable;

/**
 * Created by napster on 29.11.17.
 * <p>
 * Unique key for entities, describing both their id and class.
 * <p>
 * Somewhat of a Type class.
 * <p>
 * The relation between I and E is controlled by the publicly available constructors / factory methods.
 */
public class EntityKey<I, E> {
    public final I id;
    public final Class<E> clazz;

    @Nonnull
    public static <I extends Serializable, E extends IEntity<I, E>> EntityKey<I, E> of(@Nonnull final I id,
                                                                                       @Nonnull final Class<E> clazz) {
        return new EntityKey<>(id, clazz);
    }

    @Nonnull
    public static <I extends Serializable, E extends IEntity<I, E>> EntityKey<I, E> of(@Nonnull final IEntity<I, E> entity) {
        return new EntityKey<>(entity.getId(), entity.getClazz());
    }

    @Nonnull
    public static <I extends Serializable, E extends SaucedEntity<I, E>> EntityKey<I, E> of(@Nonnull final SaucedEntity<I, E> entity) {
        return new EntityKey<>(entity.getId(), entity.getClazz());
    }

    protected EntityKey(@Nonnull final I id, @Nonnull final Class<E> clazz) {
        this.id = id;
        this.clazz = clazz;
    }

    @Override
    @Nonnull
    public String toString() {
        return "E:" + this.clazz.getSimpleName() + ":" + this.id;
    }
}
