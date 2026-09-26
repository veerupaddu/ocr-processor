package ai.medhaleak.ocrprocessor.service;

import ai.medhaleak.ocrprocessor.testing.Evidence;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TesseractOcrEngineReadabilityTest {

    @Test
    void readableFromTsv_dropsLowConfidenceNoise() {
        String tsv = """
                level	page_num	block_num	par_num	line_num	word_num	left	top	width	height	conf	text
                5	1	1	1	1	1	10	10	20	20	12.0	ae
                5	1	1	1	1	2	40	10	80	20	96.0	End-to-end
                5	1	2	1	1	1	10	40	40	20	91.0	security
                5	1	2	1	1	2	60	40	20	20	88.0	for
                5	1	2	1	1	3	90	40	30	20	90.0	your
                5	1	3	1	1	1	10	70	10	10	40.0	%
                5	1	3	1	2	1	10	90	20	20	93.0	AI
                5	1	3	1	2	2	40	90	80	20	94.0	investments
                5	1	4	1	1	1	10	120	20	20	15.0	Ds
                5	1	4	1	2	1	10	150	70	20	92.0	Microso**
                """;

        String text = TesseractOcrEngine.readableFromTsv(tsv);
        assertThat(text).isEqualTo("End-to-end\nsecurity for your\nAI investments\nMicroso");
        Evidence.record("TSV words conf 12 ae, 96 End-to-end, 91 security, 88 for, 90 your, 40 %, 93 AI, 94 investments, 15 Ds, 92 Microso**",
                "words below 50 dropped", "text=\"" + text.replace('\n', ' ') + "\"");
    }
}
