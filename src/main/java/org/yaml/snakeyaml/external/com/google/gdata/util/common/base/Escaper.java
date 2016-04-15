/* Copyright (c) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.yaml.snakeyaml.external.com.google.gdata.util.common.base;

/**
 * An object that converts literal text into a format safe for inclusion in a
 * particular context (such as an XML document). Typically (but not always), the
 * inverse process of "unescaping" the text is performed automatically by the
 * relevant parser.
 *
 * <p>
 * For example, an XML escaper would convert the literal string
 * {@code "Foo<Bar>"} into {@code "Foo&lt;Bar&gt;"} to prevent {@code "<Bar>"}
 * from being confused with an XML tag. When the resulting XML document is
 * parsed, the parser API will return this text as the original literal string
 * {@code "Foo<Bar>"}.
 *
 * <p>
 * An {@code Escaper} instance is required to be stateless, and safe when used
 * concurrently by multiple threads.
 *
 *
 *
 */
public interface Escaper {
    /**
     * Returns the escaped form of a given literal string.
     *
     * <p>
     * Note that this method may treat input characters differently depending on
     * the specific escaper implementation.
     *
     * @param string
     *            the literal string to be escaped
     * @return the escaped form of {@code string}
     * @throws NullPointerException
     *             if {@code string} is null
     * @throws IllegalArgumentException
     *             if {@code string} contains badly formed UTF-16 or cannot be
     *             escaped for any other reason
     */
    public String escape(String string);

    /**
     * Returns an {@code Appendable} instance which automatically escapes all
     * text appended to it before passing the resulting text to an underlying
     * {@code Appendable}.
     *
     * <p>
     * Note that this method may treat input characters differently depending on
     * the specific escaper implementation.
     *
     * @param out
     *            the underlying {@code Appendable} to append escaped output to
     * @return an {@code Appendable} which passes text to {@code out} after
     *         escaping it.
     */
    public Appendable escape(Appendable out);
}
