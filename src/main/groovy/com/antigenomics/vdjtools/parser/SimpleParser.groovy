package com.antigenomics.vdjtools.parser

import com.antigenomics.vdjtools.ClonotypeJ

/**
 Copyright 2014 Mikhail Shugay (mikhail.shugay@gmail.com)

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
class SimpleParser extends ClonotypeParser {
    @Override
    protected ClonotypeJ parse(String clonotypeString) {
        def splitString = clonotypeString.split(software.delimiter)

        def count = splitString[0].toInteger()
        def freq = splitString[1].toDouble()

        String cdr1nt = null, cdr2nt = null, cdr3nt, cdr1aa = null, cdr2aa = null, cdr3aa

        cdr3nt = splitString[2]
        cdr3aa = splitString[3]

        String v, d, j
        (v, d, j) = extractVDJ(splitString[4..-1])

        boolean inFrame = !(cdr3aa.contains("~") || cdr3aa.contains("?")),
                noStop = !cdr3aa.contains("*"),
                isComplete = true

        new ClonotypeJ(sample, count, freq,
                [-1, -1, -1, -1] as int[], v, d, j,
                cdr1nt, cdr2nt, cdr3nt,
                cdr1aa, cdr2aa, cdr3aa,
                inFrame, noStop, isComplete,
                null)
    }
}
