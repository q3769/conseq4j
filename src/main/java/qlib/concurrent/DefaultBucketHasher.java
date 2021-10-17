/*
 * The MIT License
 *
 * Copyright 2021 Qingtian Wang.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package qlib.concurrent;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 *
 * @author q3769
 */
public class DefaultBucketHasher implements ConsistentBucketHasher {

    private static final HashFunction HASH_FUNCTION = Hashing.murmur3_128();

    public static DefaultBucketHasher ofBuckets(int buckets) {
        return new DefaultBucketHasher(buckets);
    }
    private final int buckets;

    private DefaultBucketHasher(Integer buckets) { 
        this.buckets = Objects.requireNonNull(buckets, "Max bucket count cannot be null");
    }

    @Override
    public int hash(Object sequenceKey) {
        Objects.requireNonNull(sequenceKey, "Sequence key cannot be null");
        if (sequenceKey instanceof CharSequence) {
            return Hashing.consistentHash(HASH_FUNCTION.hashUnencodedChars((CharSequence) sequenceKey), this.buckets);
        }
        if (sequenceKey instanceof Long) {
            return Hashing.consistentHash(HASH_FUNCTION.hashLong((Long) sequenceKey), this.buckets);
        }
        if (sequenceKey instanceof Integer) {
            return Hashing.consistentHash(HASH_FUNCTION.hashInt((Integer) sequenceKey), this.buckets);
        }
        if (sequenceKey instanceof byte[]) {
            return Hashing.consistentHash(HASH_FUNCTION.hashBytes((byte[]) sequenceKey), this.buckets);
        }
        if (sequenceKey instanceof ByteBuffer) {
            return Hashing.consistentHash(HASH_FUNCTION.hashBytes((ByteBuffer) sequenceKey), this.buckets);
        }
        return Hashing.consistentHash(HASH_FUNCTION.hashInt(sequenceKey.hashCode()), this.buckets);
    }

    @Override
    public int getBuckets() {
        return this.buckets;
    }

}
