package svenmeier.coxswain.rower.water;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import svenmeier.coxswain.gym.Measurement;
import svenmeier.coxswain.rower.water.usb.ITransfer;

import static org.junit.Assert.assertEquals;

/**
 */
public class Protocol3Test {

	Measurement measurement = new Measurement();

	@Test
	public void test() throws Exception {

		TestTransfer transfer = new TestTransfer();
		TestTrace trace = new TestTrace();

		Protocol3 protocol = new Protocol3(transfer, trace);
		assertEquals(1200, transfer.baudrate);
		assertEquals(8, transfer.dataBits);
		assertEquals(TestTransfer.PARITY_NONE, transfer.parity);
		assertEquals(ITransfer.STOP_BIT_1_0, transfer.stopBits);
		assertEquals(false, transfer.tx);

		// distance +2.5
		transfer.setupInput(new byte[]{(byte) 0xFE, (byte) 0x19});
		protocol.transfer(measurement);
		assertEquals(2, measurement.distance);
		// distance +0.5
		transfer.setupInput(new byte[]{(byte) 0xFE, (byte) 0x05});
		protocol.transfer(measurement);
		assertEquals(3, measurement.distance);


		transfer.setupInput(new byte[]{(byte) 0xFC});
		protocol.transfer(measurement);
		assertEquals(1, measurement.strokes);


		transfer.setupInput(new byte[]{(byte) 0xFD, (byte) 0x01, (byte) 0x02});
		protocol.transfer(measurement);


		transfer.setupInput(new byte[]{(byte) 0xFB, (byte) 0x01});
		protocol.transfer(measurement);
		assertEquals(1, measurement.pulse);

		transfer.setupInput(new byte[]{(byte) 0xFB, (byte) 0x01});
		protocol.transfer(measurement);
		assertEquals(1, measurement.pulse);


		transfer.setupInput(new byte[]{(byte) 0xFF, (byte) 0x01, (byte) 0x02});
		protocol.transfer(measurement);
		assertEquals(1, measurement.strokeRate);
		assertEquals(20, measurement.speed);

		transfer.setupInput(new byte[]{(byte) 0xFF, (byte) 0x01, (byte) 0x02});
		protocol.transfer(measurement);
		assertEquals(1, measurement.strokeRate);
		assertEquals(20, measurement.speed);

		transfer.setupInput(new byte[]{(byte) 0x01, (byte) 0x02, (byte) 0x03});
		protocol.transfer(measurement);

		assertEquals("#protocol 3<FE 19<FE 05<FC<FD 01 02<FB 01<FB 01<FF 01 02<FF 01 02<01<02<03", trace.toString());
	}

	@Test
	public void trace() throws IOException {

		BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/waterrower.trace")));

		TestTransfer transfer = new TestTransfer();
		TestTrace trace = new TestTrace();

		Protocol3 protocol = new Protocol3(transfer, trace);

		while (true) {
			String line = reader.readLine();

			if (line == null) {
				break;
			} else if (line.startsWith("<")) {
				String[] hexes = line.substring(1).split("\\s+");
				byte[] bytes = new byte[hexes.length];
				for (int h = 0; h < hexes.length; h++) {
					bytes[h] = (byte)Integer.parseInt(hexes[h], 16);
				}
				transfer.setupInput(bytes);
				protocol.transfer(measurement);
			}
		}
		assertEquals(363, measurement.strokes);
		assertEquals(1510, measurement.distance);
	}
}