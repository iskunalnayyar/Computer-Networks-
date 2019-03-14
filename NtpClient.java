import java.io.IOException;
import java.net.*;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;


/**
 * @author Kunal Nayyar
 */
class NtpMessage {
    //This is a two-bit code warning of an impending leap second to be inserted/deleted in the last minute of the current day.
    private byte leapIndicator = 0;

    //The version number 4 is for Version 4 (IPv4, IPv6 and OSI).
    private byte version = 4;

    //This value indicates the mode, 4 is for the server
    private byte mode;

    //This value indicates the maximum interval between successive messages,
    //in seconds to the nearest power of two.
    public byte pollInterval = 0;


    //  This is the time at which the local clock was last set or corrected, in
    double referenceTimestamp = 0;

    // Time at which the request departed the client
    double originateTimestamp = 0;

    // Time at which the request arrived at the server, in seconds
    double receiveTimestamp = 0;

    //Time at which the reply departed the server for the client,
    double transmitTimestamp = 0;


    /**
     * Constructs a new NtpMessage from an array of bytes.
     */
    NtpMessage(byte[] array) {
        // See the packet format diagram in RFC 2030 for details
        leapIndicator = (byte) ((array[0] >> 6) & 0x3);
        version = (byte) ((array[0] >> 3) & 0x7);
        mode = (byte) (array[0] & 0x7);
        pollInterval = array[2];


        referenceTimestamp = decodeTimestamp(array, 16);
        originateTimestamp = decodeTimestamp(array, 24);
        receiveTimestamp = decodeTimestamp(array, 32);
        transmitTimestamp = decodeTimestamp(array, 40);
    }


    /**
     * Constructs a new NtpMessage in client -> server mode, and sets the
     * transmit timestamp to the current time.
     */
    public NtpMessage() {
        // appropriate default values.
        this.mode = 3; //client
        this.transmitTimestamp = (System.currentTimeMillis() / 1000.0) + 2208988800.0;
    }


    /**
     * This method constructs the data bytes of a raw NTP packet.
     */
    public byte[] toByteArray() {
        // All bytes are automatically set to 0
        byte[] p = new byte[48];

        p[0] = (byte) (leapIndicator << 6 | version << 3 | mode);
        p[2] = (byte) pollInterval;

        encodeTimestamp(p, 16, referenceTimestamp);
        encodeTimestamp(p, 24, originateTimestamp);
        encodeTimestamp(p, 32, receiveTimestamp);
        encodeTimestamp(p, 40, transmitTimestamp);

        return p;
    }



    /**
     * Returns a timestamp (number of seconds since 00:00 1-Jan-1900) as a
     * formatted date/time string.
     */
    public static String timestampToString(double timestamp) {
        if (timestamp == 0) return "0";

        // timestamp is relative to 1900, utc is used by Java and is relative
        // to 1970
        double utc = timestamp - (2208988800.0);

        // milliseconds
        long ms = (long) (utc * 1000.0);

        // date/time
        String date = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss").format(new Date(ms));

        // fraction
        double fraction = timestamp - ((long) timestamp);
        String fractionSting = new DecimalFormat(".000000").format(fraction);

        return date + fractionSting;
    }




    /**
     * Converts an unsigned byte to a short.
     */
    private static short unsignedByteToShort(byte b) {
        if ((b & 0x80) == 0x80) return (short) (128 + (b & 0x7f));
        else return (short) b;
    }


    /**
     * Will read 8 bytes of a message beginning at pointer
     * and return it as a double, according to the NTP 64-bit timestamp
     * format.
     */
    private static double decodeTimestamp(byte[] array, int pointer) {
        double r = 0.0;

        for (int i = 0; i < 8; i++) {
            r += unsignedByteToShort(array[pointer + i]) * Math.pow(2, (3 - i) * 8);
        }
        return r;
    }


    /**
     * Encodes a timestamp in the specified position in the message
     */
    static void encodeTimestamp(byte[] array, int pointer, double timestamp) {
        // Converts a double into a 64-bit fixed point
        for (int i = 0; i < 8; i++) {
            double base = Math.pow(2, (3 - i) * 8);
            array[pointer + i] = (byte) (timestamp / base);
            timestamp = timestamp - (unsignedByteToShort(array[pointer + i]) * base);
        }

    }
}

public class NtpClient {
    /**
     * Client
     */
    static double newtime;
    static double  localClockOffset = 0;

    public static void main(String[] args) throws IOException {
        String ipaddr;
        ipaddr = args[0];
        newtime = System.currentTimeMillis() / 1000.0 + 2208988800.0;


        while (true) {
            // Send request

            DatagramSocket socket = new DatagramSocket();
            InetAddress ip = InetAddress.getByName(ipaddr);

            byte[] buf = new NtpMessage().toByteArray();
            DatagramPacket packet =
                    new DatagramPacket(buf, buf.length, ip, 123);

            // Set the transmit timestamp *just* before sending the packet
            NtpMessage.encodeTimestamp(packet.getData(), 40, (newtime));

            socket.send(packet);

            // Get response
            System.out.println("NTP request sent, waiting for response...\n");
            packet = new DatagramPacket(buf, buf.length);

            socket.receive(packet);

            double destinationTimeStamp = newtime;

            // Process response
            NtpMessage msg = new NtpMessage(packet.getData());

            localClockOffset = ((msg.receiveTimestamp - msg.originateTimestamp) +
                            (msg.transmitTimestamp - destinationTimeStamp)) / 2;

            // Display response
            System.out.println("NTP server: " + ip);

            System.out.println("Dest. timestamp:     " +
                    NtpMessage.timestampToString(destinationTimeStamp));

            System.out.println("Local clock offset: " +
                    new DecimalFormat("0.00").format(localClockOffset * 1000) + " ms");

            System.out.println("Server clock time: " + msg.timestampToString(localClockOffset + msg.originateTimestamp));

            socket.close();
            // send a new packet with the corrected reference timestamp.

            double dtemp;

            if (msg.pollInterval == 0)
                msg.pollInterval = 1;
            for (int i = 0; i < msg.pollInterval; i++) {
                dtemp = localClockOffset / (msg.pollInterval);
                // adjust_time, since we cannot adjust current system's time:
                //System.out.println("Adjust time here");
                localClockOffset = localClockOffset - dtemp;
                newtime = newtime + dtemp;
            }
            //newtime = newtime + localClockOffset;

            System.out.println("Local System time now is: " + msg.timestampToString(newtime));

            //TimeUnit.SECONDS.sleep(3);
            System.out.println();
        }

        }
    }
