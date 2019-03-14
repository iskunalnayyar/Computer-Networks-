import java.io.*;
import java.net.*;


public class Sender {
    /**
     *  Sender (Buoy)
     *
     * @author Kunal Nayyar
     * @param args
     * @throws Exception
     */

    public static void main(String args[]) throws Exception {
        // Get the address, port and name of file to send over UDP
        final String hostName = args[0];
        final int port = Integer.parseInt(args[1]);
        final String fileName = args[2];
        InetAddress ip = InetAddress.getByName(hostName);
        DatagramSocket check = new DatagramSocket();
        byte [] buff = new byte[1];
//

        buff[0] = 64;
        DatagramPacket check_Send = new DatagramPacket(buff, buff.length, ip, port);
        check.send(check_Send);

        boolean ackRecievedCorrect = false, ackPacketReceived = false;


        while (!ackRecievedCorrect) {
            // Check for an ack of the sent control packet.
            DatagramPacket ackpack = new DatagramPacket(buff, buff.length);

            try {
                check.setSoTimeout(500);
                check.receive(ackpack);
                ackPacketReceived = true;
            } catch (SocketTimeoutException e) {
                System.out.println("Socket timed out waiting for an ack");
                ackPacketReceived = false;
                //e.printStackTrace();
            }

            // Break if there is an ack so that the next packet can be sent
            if (ackPacketReceived) {
                System.out.println("Ack received: Sequence Number = " + buff[0]);
                break;
            } else { // Resend packet
                check.send(check_Send);
                System.out.println("Resending: Sequence Number = " + buff[0]);

            }
        }

        check.close();

        createAndSend(hostName, port, fileName);
    }

    public static void createAndSend(String hostName, int port, String fileName) throws IOException {
        System.out.println("Sending the file");

        // Create the socket, set the address and create the file to be sent
        DatagramSocket socket = new DatagramSocket();
        InetAddress address = InetAddress.getByName(hostName);
        File file = new File(fileName);

        // Create a byte array to store the filestream
        InputStream inFromFile = new FileInputStream(file);
        byte[] fileByteArray = new byte[(int) file.length()];
        inFromFile.read(fileByteArray);



        // Create a flag to indicate the last message and a 16-bit sequence number
        int sequenceNumber = 0;
        boolean lastMessageFlag;

        // 16-bit sequence number for acknowledged packets
        int ackSequenceNumber = 0;


        // For as each message we will create
        for (int i = 0; i < fileByteArray.length; i = i + 5117) {

            sequenceNumber += 1;
            byte[] message = new byte[5120];

            // Set the first and second bytes of the message to the sequence number
            message[0] = (byte) (sequenceNumber >> 8);
            message[1] = (byte) (sequenceNumber);

            // Set flag to 1 if packet is last packet and store it in third byte of header
            if ((i + 5117) >= fileByteArray.length) {
                lastMessageFlag = true;
                message[2] = (byte) (1);
            } else { // If not last message store flag as 0
                lastMessageFlag = false;
                message[2] = (byte) (0);
            }

            // Copy the bytes for the message to the message array
            if (!lastMessageFlag) {
                for (int j = 0; j <= 5116; j++) {
                    message[j + 3] = fileByteArray[i + j];
                }
            } else if (lastMessageFlag) { // If it is the last message
                for (int j = 0; j < (fileByteArray.length - i); j++) {
                    message[j + 3] = fileByteArray[i + j];
                }
            }

            // Send the message
            DatagramPacket sendPacket = new DatagramPacket(message, message.length, address, port);
            socket.send(sendPacket);
            System.out.println("Sent: Sequence number = " + sequenceNumber + ", Flag = " + lastMessageFlag);

            // For verifying the acknowledgements
            boolean ackRecievedCorrect = false, ackPacketReceived = false;

            while (!ackRecievedCorrect) {
                // Check for an ack
                byte[] ack = new byte[2];
                DatagramPacket ackpack = new DatagramPacket(ack, ack.length);

                try {
                    socket.setSoTimeout(50);
                    socket.receive(ackpack);
                    ackSequenceNumber = ((ack[0] & 0xff) << 8) + (ack[1] & 0xff);
                    ackPacketReceived = true;
                } catch (SocketTimeoutException e) {
                    System.out.println("Socket timed out waiting for an ack");
                    ackPacketReceived = false;
                    //e.printStackTrace();
                }

                // Break if there is an ack so that the next packet can be sent
                if ((ackSequenceNumber == sequenceNumber) && (ackPacketReceived)) {
                    System.out.println("Ack received: Sequence Number = " + ackSequenceNumber);
                    break;
                } else { // Resend packet
                    socket.send(sendPacket);
                    System.out.println("Resending: Sequence Number = " + sequenceNumber);

                }
            }
        }

        socket.close();
        System.out.println("File " + fileName + " has been sent");

    }
}

