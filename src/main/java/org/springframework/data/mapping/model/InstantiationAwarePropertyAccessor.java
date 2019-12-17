/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.mapping.model;

import org.springframework.data.annotation.PersistenceConstructor;
import org.springframework.data.convert.EntityInstantiator;
import org.springframework.data.convert.EntityInstantiators;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.mapping.PersistentPropertyAccessor;
import org.springframework.data.mapping.PreferredConstructor;
import org.springframework.data.mapping.PreferredConstructor.Parameter;
import org.springframework.data.util.ReflectionUtils;
import org.springframework.lang.Nullable;

/**
 * A {@link PersistentPropertyAccessor} that will use an entity's {@link PersistenceConstructor} to create a new
 * instance of it to apply a new value for a given {@link PersistentProperty}. Will only be used if the
 * {@link PersistentProperty} is to be applied on a completely immutable entity type exposing a persistence constructor.
 *
 * @author Oliver Drotbohm
 */
public class InstantiationAwarePropertyAccessor<T> implements PersistentPropertyAccessor<T> {

	private final PersistentPropertyAccessor<T> delegate;
	private final EntityInstantiators instantiators;

	private T bean;

	/**
	 *
	 */
	public InstantiationAwarePropertyAccessor(PersistentPropertyAccessor<T> delegate, EntityInstantiators instantiators) {

		this.delegate = delegate;
		this.instantiators = instantiators;
		this.bean = delegate.getBean();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mapping.PersistentPropertyAccessor#setProperty(org.springframework.data.mapping.PersistentProperty, java.lang.Object)
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void setProperty(PersistentProperty<?> property, @Nullable Object value) {

		PersistentEntity<?, ?> owner = property.getOwner();

		if (!property.isImmutable() || property.getWither() != null || ReflectionUtils.isKotlinClass(owner.getType())) {

			delegate.setProperty(property, value);
			this.bean = delegate.getBean();

			return;
		}

		PreferredConstructor<?, ?> constructor = owner.getPersistenceConstructor();

		if (constructor == null) {
			throw new IllegalStateException(
					String.format("Cannot set property %s because no setter, wither or copy constructor exists for %s!",
							property.getName(), owner.getType()));
		}

		constructor.getParameters().stream().forEach(it -> {
			if (it.getName() == null) {
				throw new IllegalStateException(
						String.format("Cannot detect parameter names of copy constructor of %s!", owner.getType()));
			}
		});

		EntityInstantiator instantiator = instantiators.getInstantiatorFor(owner);

		this.bean = (T) instantiator.createInstance(owner, new ParameterValueProvider() {

			/*
			 * (non-Javadoc)
			 * @see org.springframework.data.mapping.model.ParameterValueProvider#getParameterValue(org.springframework.data.mapping.PreferredConstructor.Parameter)
			 */
			@Override
			@Nullable
			@SuppressWarnings("null")
			public Object getParameterValue(Parameter parameter) {

				return property.getName().equals(parameter.getName()) //
						? value
						: delegate.getProperty(owner.getRequiredPersistentProperty(parameter.getName()));
			}
		});
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mapping.PersistentPropertyAccessor#getProperty(org.springframework.data.mapping.PersistentProperty)
	 */
	@Nullable
	@Override
	public Object getProperty(PersistentProperty<?> property) {
		return delegate.getProperty(property);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mapping.PersistentPropertyAccessor#getBean()
	 */
	@Override
	public T getBean() {
		return this.bean;
	}
}
