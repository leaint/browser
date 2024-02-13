/*
 * Copyright 2009 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.integration.android;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * <p>Encapsulates the result of a barcode scan invoked through {@link IntentIntegrator}.</p>
 *
 * @author Sean Owen
 */
public final class IntentResult {

  @Nullable
  private final String contents;
  @Nullable
  private final String formatName;
  @Nullable
  private final byte[] rawBytes;
  @Nullable
  private final Integer orientation;
  @Nullable
  private final String errorCorrectionLevel;

  IntentResult() {
    this(null, null, null, null, null);
  }

  IntentResult(@Nullable String contents,
               @Nullable String formatName,
               @Nullable byte[] rawBytes,
               @Nullable Integer orientation,
               @Nullable String errorCorrectionLevel) {
    this.contents = contents;
    this.formatName = formatName;
    this.rawBytes = rawBytes;
    this.orientation = orientation;
    this.errorCorrectionLevel = errorCorrectionLevel;
  }

  /**
   * @return raw content of barcode
   */
  @Nullable
  public String getContents() {
    return contents;
  }

  /**
   * @return name of format, like "QR_CODE", "UPC_A". See {@code BarcodeFormat} for more format names.
   */
  @Nullable
  public String getFormatName() {
    return formatName;
  }

  /**
   * @return raw bytes of the barcode content, if applicable, or null otherwise
   */
  public byte[] getRawBytes() {
    return rawBytes;
  }

  /**
   * @return rotation of the image, in degrees, which resulted in a successful scan. May be null.
   */
  @Nullable
  public Integer getOrientation() {
    return orientation;
  }

  /**
   * @return name of the error correction level used in the barcode, if applicable
   */
  @Nullable
  public String getErrorCorrectionLevel() {
    return errorCorrectionLevel;
  }
  
  @NonNull
  @Override
  public String toString() {
    int rawBytesLength = rawBytes == null ? 0 : rawBytes.length;
    return "Format: " + formatName + '\n' +
        "Contents: " + contents + '\n' +
        "Raw bytes: (" + rawBytesLength + " bytes)\n" +
        "Orientation: " + orientation + '\n' +
        "EC level: " + errorCorrectionLevel + '\n';
  }

}
