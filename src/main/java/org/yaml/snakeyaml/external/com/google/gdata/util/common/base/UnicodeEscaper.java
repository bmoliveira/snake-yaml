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

import java.io.IOException;

public abstract class UnicodeEscaper implements Escaper {
    /** The amount of padding (chars) to use when growing the escape buffer. */
    private static final int DEST_PAD = 32;

    /**
     * Returns the escaped form of the given Unicode code point, or {@code null}
     * if this code point does not need to be escaped. When called as part of an
     * escaping operation, the given code point is guaranteed to be in the range
     * {@code 0 <= cp <= Character#MAX_CODE_POINT}.
     *
     * <p>
     * If an empty array is returned, this effectively strips the input
     * character from the resulting text.
     *
     * <p>
     * If the character does not need to be escaped, this method should return
     * {@code null}, rather than an array containing the character
     * representation of the code point. This enables the escaping algorithm to
     * perform more efficiently.
     *
     * <p>
     * If the implementation of this method cannot correctly handle a particular
     * code point then it should either throw an appropriate runtime exception
     * or return a suitable replacement character. It must never silently
     * discard invalid input as this may constitute a security risk.
     *
     * @param cp
     *            the Unicode code point to escape if necessary
     * @return the replacement characters, or {@code null} if no escaping was
     *         needed
     */
    protected abstract char[] escape(int cp);


    protected int nextEscapeIndex(CharSequence csq, int start, int end) {
        int index = start;
        while (index < end) {
            int cp = codePointAt(csq, index, end);
            if (cp < 0 || escape(cp) != null) {
                break;
            }
            index += Character.isSupplementaryCodePoint(cp) ? 2 : 1;
        }
        return index;
    }


    public String escape(String string) {
        int end = string.length();
        int index = nextEscapeIndex(string, 0, end);
        return index == end ? string : escapeSlow(string, index);
    }


    protected final String escapeSlow(String s, int index) {
        int end = s.length();

        // Get a destination buffer and setup some loop variables.
        char[] dest = DEST_TL.get();
        int destIndex = 0;
        int unescapedChunkStart = 0;

        while (index < end) {
            int cp = codePointAt(s, index, end);
            if (cp < 0) {
                throw new IllegalArgumentException("Trailing high surrogate at end of input");
            }
            char[] escaped = escape(cp);
            if (escaped != null) {
                int charsSkipped = index - unescapedChunkStart;

                // This is the size needed to add the replacement, not the full
                // size needed by the string. We only regrow when we absolutely
                // must.
                int sizeNeeded = destIndex + charsSkipped + escaped.length;
                if (dest.length < sizeNeeded) {
                    int destLength = sizeNeeded + (end - index) + DEST_PAD;
                    dest = growBuffer(dest, destIndex, destLength);
                }
                // If we have skipped any characters, we need to copy them now.
                if (charsSkipped > 0) {
                    s.getChars(unescapedChunkStart, index, dest, destIndex);
                    destIndex += charsSkipped;
                }
                if (escaped.length > 0) {
                    System.arraycopy(escaped, 0, dest, destIndex, escaped.length);
                    destIndex += escaped.length;
                }
            }
            unescapedChunkStart = index + (Character.isSupplementaryCodePoint(cp) ? 2 : 1);
            index = nextEscapeIndex(s, unescapedChunkStart, end);
        }

        // Process trailing unescaped characters - no need to account for
        // escaped
        // length or padding the allocation.
        int charsSkipped = end - unescapedChunkStart;
        if (charsSkipped > 0) {
            int endIndex = destIndex + charsSkipped;
            if (dest.length < endIndex) {
                dest = growBuffer(dest, destIndex, endIndex);
            }
            s.getChars(unescapedChunkStart, end, dest, destIndex);
            destIndex = endIndex;
        }
        return new String(dest, 0, destIndex);
    }


    public Appendable escape(final Appendable out) {
        assert out != null;

        return new Appendable() {
            int pendingHighSurrogate = -1;
            char[] decodedChars = new char[2];

            public Appendable append(CharSequence csq) throws IOException {
                return append(csq, 0, csq.length());
            }

            public Appendable append(CharSequence csq, int start, int end) throws IOException {
                int index = start;
                if (index < end) {
                    // This is a little subtle: index must never reference the
                    // middle of a
                    // surrogate pair but unescapedChunkStart can. The first
                    // time we enter
                    // the loop below it is possible that index !=
                    // unescapedChunkStart.
                    int unescapedChunkStart = index;
                    if (pendingHighSurrogate != -1) {
                        // Our last append operation ended halfway through a
                        // surrogate pair
                        // so we have to do some extra work first.
                        char c = csq.charAt(index++);
                        if (!Character.isLowSurrogate(c)) {
                            throw new IllegalArgumentException(
                                    "Expected low surrogate character but got " + c);
                        }
                        char[] escaped = escape(Character.toCodePoint((char) pendingHighSurrogate,
                                c));
                        if (escaped != null) {
                            // Emit the escaped character and adjust
                            // unescapedChunkStart to
                            // skip the low surrogate we have consumed.
                            outputChars(escaped, escaped.length);
                            unescapedChunkStart += 1;
                        } else {
                            // Emit pending high surrogate (unescaped) but do
                            // not modify
                            // unescapedChunkStart as we must still emit the low
                            // surrogate.
                            out.append((char) pendingHighSurrogate);
                        }
                        pendingHighSurrogate = -1;
                    }
                    while (true) {
                        // Find and append the next subsequence of unescaped
                        // characters.
                        index = nextEscapeIndex(csq, index, end);
                        if (index > unescapedChunkStart) {
                            out.append(csq, unescapedChunkStart, index);
                        }
                        if (index == end) {
                            break;
                        }
                        // If we are not finished, calculate the next code
                        // point.
                        int cp = codePointAt(csq, index, end);
                        if (cp < 0) {
                            // Our sequence ended half way through a surrogate
                            // pair so just
                            // record the state and exit.
                            pendingHighSurrogate = -cp;
                            break;
                        }
                        // Escape the code point and output the characters.
                        char[] escaped = escape(cp);
                        if (escaped != null) {
                            outputChars(escaped, escaped.length);
                        } else {
                            // This shouldn't really happen if nextEscapeIndex
                            // is correct but
                            // we should cope with false positives.
                            int len = Character.toChars(cp, decodedChars, 0);
                            outputChars(decodedChars, len);
                        }
                        // Update our index past the escaped character and
                        // continue.
                        index += (Character.isSupplementaryCodePoint(cp) ? 2 : 1);
                        unescapedChunkStart = index;
                    }
                }
                return this;
            }

            public Appendable append(char c) throws IOException {
                if (pendingHighSurrogate != -1) {
                    // Our last append operation ended halfway through a
                    // surrogate pair
                    // so we have to do some extra work first.
                    if (!Character.isLowSurrogate(c)) {
                        throw new IllegalArgumentException(
                                "Expected low surrogate character but got '" + c + "' with value "
                                        + (int) c);
                    }
                    char[] escaped = escape(Character.toCodePoint((char) pendingHighSurrogate, c));
                    if (escaped != null) {
                        outputChars(escaped, escaped.length);
                    } else {
                        out.append((char) pendingHighSurrogate);
                        out.append(c);
                    }
                    pendingHighSurrogate = -1;
                } else if (Character.isHighSurrogate(c)) {
                    // This is the start of a (split) surrogate pair.
                    pendingHighSurrogate = c;
                } else {
                    if (Character.isLowSurrogate(c)) {
                        throw new IllegalArgumentException("Unexpected low surrogate character '"
                                + c + "' with value " + (int) c);
                    }
                    // This is a normal (non surrogate) char.
                    char[] escaped = escape(c);
                    if (escaped != null) {
                        outputChars(escaped, escaped.length);
                    } else {
                        out.append(c);
                    }
                }
                return this;
            }

            private void outputChars(char[] chars, int len) throws IOException {
                for (int n = 0; n < len; n++) {
                    out.append(chars[n]);
                }
            }
        };
    }

    
    protected static final int codePointAt(CharSequence seq, int index, int end) {
        if (index < end) {
            char c1 = seq.charAt(index++);
            if (c1 < Character.MIN_HIGH_SURROGATE || c1 > Character.MAX_LOW_SURROGATE) {
                // Fast path (first test is probably all we need to do)
                return c1;
            } else if (c1 <= Character.MAX_HIGH_SURROGATE) {
                // If the high surrogate was the last character, return its
                // inverse
                if (index == end) {
                    return -c1;
                }
                // Otherwise look for the low surrogate following it
                char c2 = seq.charAt(index);
                if (Character.isLowSurrogate(c2)) {
                    return Character.toCodePoint(c1, c2);
                }
                throw new IllegalArgumentException("Expected low surrogate but got char '" + c2
                        + "' with value " + (int) c2 + " at index " + index);
            } else {
                throw new IllegalArgumentException("Unexpected low surrogate character '" + c1
                        + "' with value " + (int) c1 + " at index " + (index - 1));
            }
        }
        throw new IndexOutOfBoundsException("Index exceeds specified range");
    }

    /**
     * Helper method to grow the character buffer as needed, this only happens
     * once in a while so it's ok if it's in a method call. If the index passed
     * in is 0 then no copying will be done.
     */
    private static final char[] growBuffer(char[] dest, int index, int size) {
        char[] copy = new char[size];
        if (index > 0) {
            System.arraycopy(dest, 0, copy, 0, index);
        }
        return copy;
    }

    /**
     * A thread-local destination buffer to keep us from creating new buffers.
     * The starting size is 1024 characters. If we grow past this we don't put
     * it back in the threadlocal, we just keep going and grow as needed.
     */
    private static final ThreadLocal<char[]> DEST_TL = new ThreadLocal<char[]>() {
        @Override
        protected char[] initialValue() {
            return new char[1024];
        }
    };
}
