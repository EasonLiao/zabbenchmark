/**
 * Licensed to the zk1931 under one or more contributor license
 * agreements.  See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.zk1931.zabbenchmark;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A utility class to serialize / deserialize commands.
 */
public final class Serializer {
  private static final Logger LOG = LoggerFactory.getLogger(Serializer.class);

  /**
   * Disables constructor.
   */
  private Serializer() {
  }

  /**
   * Serializes a Serializable object to ByteBuffer.
   */
  public static ByteBuffer serialize(Serializable obj) throws IOException {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
         ObjectOutputStream oos = new ObjectOutputStream(bos)) {
      oos.writeObject(obj);
      oos.close();
      return ByteBuffer.wrap(bos.toByteArray());
    }
  }

  /**
   * Deserializes a ByteBuffer to Serializable object.
   */
  public static Serializable deserialize(byte[] bytes) {
    try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
         ObjectInputStream ois = new ObjectInputStream(bis)) {
      return (Serializable)ois.readObject();
    } catch (ClassNotFoundException|IOException ex) {
      throw new RuntimeException("Failed to deserialize ByteBuffer");
    }
  }
}
