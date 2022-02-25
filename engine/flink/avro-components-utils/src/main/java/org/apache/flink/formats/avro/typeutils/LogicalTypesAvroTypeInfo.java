/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.formats.avro.typeutils;

import org.apache.avro.specific.SpecificRecordBase;
import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.typeutils.TypeSerializer;

// TODO: This class is not used now, but should be used in our TypeInformation mechanisms (for messages passed between operators and for managed stated)
/**
 * Special type information to generate a special AvroTypeInfo for Avro POJOs (implementing SpecificRecordBase, the typed Avro POJOs)
 *
 * <p>Proceeding: It uses a regular pojo type analysis and replaces all {@code GenericType<CharSequence>} with a {@code GenericType<avro.Utf8>}.
 * All other types used by Avro are standard Java types.
 * Only strings are represented as CharSequence fields and represented as Utf8 classes at runtime.
 * CharSequence is not comparable. To make them nicely usable with field expressions, we replace them here
 * by generic type infos containing Utf8 classes (which are comparable),
 *
 * <p>This class is checked by the AvroPojoTest.
 */
public class LogicalTypesAvroTypeInfo<T extends SpecificRecordBase> extends AvroTypeInfo<T> {

	private static final long serialVersionUID = -8635380660085215330L;

	/**
	 * Creates a new Avro type info for the given class.
	 */
	public LogicalTypesAvroTypeInfo(Class<T> typeClass) {
		super(typeClass);
	}

	@Override
	public TypeSerializer<T> createSerializer(ExecutionConfig config) {
		return new LogicalTypesAvroSerializer<>(getTypeClass());
	}

}
