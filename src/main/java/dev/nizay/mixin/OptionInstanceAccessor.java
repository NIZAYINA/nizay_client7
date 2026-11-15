package dev.nizay.mixin;

import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Прямая запись значения опции в обход проверки диапазона (гамма 1500%). */
@Mixin(OptionInstance.class)
public interface OptionInstanceAccessor<T> {
	@Accessor("value")
	void nizay$setValue(T value);
}
