/*
 * Copyright 2013-2014 Mikhail Shugay (mikhail.shugay@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Last modified on 23.11.2014 by mikesh
 */

package com.antigenomics.vdjtools.pwm

import com.antigenomics.vdjtools.Clonotype
import com.antigenomics.vdjtools.util.CommonUtil
import com.google.common.util.concurrent.AtomicDouble
import com.google.common.util.concurrent.AtomicDoubleArray

import java.util.concurrent.atomic.AtomicInteger

class CdrPwm {
    private final CdrPwmGrid parent
    private final CdrPwmGrid.Cell cell
    private final AtomicDoubleArray pwm
    private final AtomicDouble freq = new AtomicDouble()
    private final AtomicInteger div = new AtomicInteger()

    CdrPwm(CdrPwmGrid parent, CdrPwmGrid.Cell cell, double[][] pwm, int div, double freq) {
        this(parent, cell)
        for (int i = 0; i < cell.length; i++)
            for (byte j = 0; j < CommonUtil.AAS.length; j++) {
                this.pwm.set(index(i, j), pwm[i][j])
            }
        this.div.set(div)
        this.freq.set(freq)
    }

    public CdrPwm(CdrPwmGrid parent, CdrPwmGrid.Cell cell) {
        this.parent = parent
        this.cell = cell
        this.pwm = new AtomicDoubleArray(cell.length * CommonUtil.AAS.length)
    }

    private int index(int pos, byte aa) {
        if (pos < 0 || pos >= cell.length)
            throw new IndexOutOfBoundsException("Bad position, $pos")
        else if (aa < 0 || aa >= CommonUtil.AAS.length)
            throw new IndexOutOfBoundsException("Bad amino acid code, $aa")
        aa * cell.length + pos
    }

    public void update(Clonotype clonotype) {
        if (clonotype.coding) {
            double freq = clonotype.freq
            this.freq.addAndGet(freq)
            this.div.incrementAndGet()
            clonotype.cdr3aa.toCharArray().eachWithIndex { char aa, int pos ->
                pwm.addAndGet(index(pos, CommonUtil.aa2code(aa)), freq)
            }
        } else {
            throw new Exception("Can only use clonotypes with coding CDR3")
        }
    }

    public double getAt(int pos, char aa) {
        this[pos, CommonUtil.aa2code(aa)]
    }

    public double getAt(int pos, byte aaCode) {
        pwm.get(index(pos, aaCode)) / freq.get()
    }

    public MajorVariant getMajorVariant(int pos) {
        def freqs = (0..<CommonUtil.AAS.length).collect { byte aaCode -> this[pos, aaCode] }
        def majorFreq = freqs.max(), majorCode = (byte) freqs.findIndexOf { it == majorFreq }
        new MajorVariant(pos, majorCode, majorFreq)
    }

    public CdrPattern extractConsensus(double positionalFrequencyThreshold) {
        def pattern = (0..<CommonUtil.AAS.length).collect { int pos ->
            def major = getMajorVariant(pos)
            major.freq >= positionalFrequencyThreshold ? major.aa : "X"
        }.join("")
        new CdrPattern(pattern, getFreq(), getDiv())
    }

    /**
     * Gets a normalized vector of character frequencies,
     * according to WebLogo paradigm, i.e. scaled to information content,
     * also subtracts a corresponding pre-built (healthy donors, n=70+ subjects) control pwm
     * @param pos position in CDR3
     * @return vector of frequencies, ordered according to CommonUtil.AAS
     */
    public double[] getNormalizedFreqs(int pos) {
        getNormalizedFreqs(pos, CdrPwmGrid.CONTROL)
    }

    /**
     * Gets a normalized vector of character frequencies,
     * according to WebLogo paradigm, i.e. scaled to information content
     * @param pos position in CDR3
     * @param control a pwm grid that will be subtracted from resulting frequencies
     * @return vector of frequencies, ordered according to CommonUtil.AAS
     */
    public double[] getNormalizedFreqs(int pos, CdrPwmGrid control) {
        getNormalizedFreqs(pos, control, false)
    }

    /**
     * Gets a normalized vector of character frequencies,
     * according to WebLogo paradigm, i.e. scaled to information content
     * @param pos position in CDR3
     * @param control a pwm grid that will be subtracted from resulting frequencies
     * @param correct whether to correct for small number of clonotypes
     * @return vector of frequencies, ordered according to CommonUtil.AAS
     */
    public double[] getNormalizedFreqs(int pos, CdrPwmGrid control, boolean correct) {
        def result = new double[CommonUtil.AAS.length]

        // In case we want to normalize by control frequencies
        def controlPwm = control ? control[cell] : null
        def controlFreqs = controlPwm ? controlPwm.getFreqs(pos) : null,
            controlNormFreqs = controlPwm ? controlPwm.getNormalizedFreqs(pos, null, true) : null

        def sum = 0
        for (byte i = 0; i < CommonUtil.AAS.length; i++) {
            result[i] = getAt(pos, i)

            if (controlFreqs) {
                result[i] /= (controlFreqs[i] > 0 ? controlFreqs[i] : 1e-9)
                sum += result[i]
            }
        }

        if (controlFreqs)
            (0..<CommonUtil.AAS.length).each { int aaCode -> result[aaCode] /= sum }

        def freqs = result.collect()

        // Sequence logo stuff
        def e = correct ? (9.5 / getDiv()) : 0.0, // correction for small number of cases
            h = -(double) freqs.sum { double f -> f > 0 ? (f * Math.log(f)) : 0 },
            R = (Math.log(20) - e - h) / Math.log(2)

        (0..<CommonUtil.AAS.length).each { int aaCode ->
            result[aaCode] *= R
            if (controlNormFreqs) {
                result[aaCode] -= controlNormFreqs[aaCode]
                if (result[aaCode] < 0)
                    result[aaCode] = 0
            }
        }

        result
    }

    public double[] getFreqs(int pos) {
        CommonUtil.AAS.collect { getAt(pos, it) }
    }

    public CdrPwmGrid.Cell getCell() {
        cell
    }

    public double getFreq() {
        freq.get() / parent.freq
    }

    public int getDiv() {
        div.get()
    }

    class MajorVariant {
        final int pos
        final byte aaCode
        final char aa
        final double freq

        MajorVariant(int pos, byte aaCode, double freq) {
            this.pos = pos
            this.aaCode = aaCode
            this.aa = CommonUtil.code2aa(aaCode)
            this.freq = freq
        }
    }
}