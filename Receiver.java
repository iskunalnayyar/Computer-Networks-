import java.io.*;
import java.net.*;

    public class Receiver {
        /**
         * Receiver (Control center)
         *
         * @author Kunal Nayyar
         * @param args
         * @throws Exception
         */

        public static void main(String args[]) throws Exception {
            System.out.println("Ready to receive the file!");

            // takes parameter Port Number, and the second as the output destination file name
            final int port = Integer.parseInt(args[0]);
            final String fileName = args[1];
            DatagramSocket sock = new DatagramSocket(port);


            boolean ackRecievedCorrect = false, ackPacketReceived = false;

            while (!ackRecievedCorrect) {
                // Check for a control pack sent from the server
                byte[] ack = new byte[1];

                DatagramPacket ackpack = new DatagramPacket(ack, ack.length);

                try {
                    sock.setSoTimeout(500);
                    sock.receive(ackpack);
                    ackPacketReceived = true;
                } catch (SocketTimeoutException e) {
                    System.out.println("Socket timed out waiting for an ack");
                    ackPacketReceived = false;
                }

                // Break if there is an ack so that the next packet can be sent
                if (ackPacketReceived) {
                    System.out.println("Ack received: Sequence Number = " + ack[0]);
                    break;
                } else { // Resend packet
                    sock.send(ackpack);
                    System.out.println("Resending: Sequence Number = " + ack[0]);

                }
            }
            sock.close();

            receiveProcess(port, fileName);
        }

        public static void receiveProcess(int port, String fileName) throws IOException {
            // Create the socket, set the address and create the file to be sent
            DatagramSocket socket = new DatagramSocket(port);
            InetAddress address;
            File file = new File(fileName);
            FileOutputStream outToFile = new FileOutputStream(file);
            FileInputStream inputStream  = new FileInputStream(file);

            // Create a flag to indicate the last message
            boolean lastMessageFlag, lastMessage = false;

            // Store sequence number
            int sequenceNumber, lastSequenceNumber = 0;



            // For each message we will receive
            while (!lastMessage) {
                // Create byte array for full message and another for file data without header
                byte[] message = new byte[5120];
                byte[] fileByteArray = new byte[5117];

                // Receive packet and retreive message
                DatagramPacket receivedPacket = new DatagramPacket(message, message.length);
                socket.setSoTimeout(0);
                socket.receive(receivedPacket);
                message = receivedPacket.getData();

                // Get port and address for sending ack
                address = receivedPacket.getAddress();
                port = receivedPacket.getPort();

                // Retrieve sequence number
                sequenceNumber = ((message[0] & 0xff) << 8) + (message[1] & 0xff);

                // Retrieve the last message flag
                if ((message[2] & 0xff) == 1) {
                    lastMessageFlag = true;
                } else {
                    lastMessageFlag = false;
                }

                if (sequenceNumber == (lastSequenceNumber + 1)) {
                    lastSequenceNumber = sequenceNumber;

                    // Retrieve data from message (without the header)
                    for (int i=3; i < 5120 ; i++) {
                        fileByteArray[i-3] = message[i];
                    }

                    // Write the message to the file and print received message
                    outToFile.write(fileByteArray);
                    System.out.println("Received: Sequence number = " + lastSequenceNumber +", Flag = " + lastMessageFlag);

                    // Send acknowledgement
                    sendanAck(lastSequenceNumber, socket, address, port);

                } else {
                    System.out.println("Expected sequence number: " + (lastSequenceNumber + 1) + " but received " + sequenceNumber + ". DISCARDING");

                    //Resend the acknowledgement
                    sendanAck(lastSequenceNumber, socket, address, port);
                }

                // Check for last message
                if (lastMessageFlag) {
                    outToFile.close();
                    lastMessage = false;
                    break;
                }
            }

            socket.close();
            System.out.println("File " + fileName + " has been received.");
        }

        public static void sendanAck(int lastSequenceNumber, DatagramSocket socket, InetAddress address, int port) throws IOException {
            // Resend acknowledgement
            byte[] ackPacket = new byte[2];
            ackPacket[0] = (byte)(lastSequenceNumber >> 8);
            ackPacket[1] = (byte)(lastSequenceNumber);
            DatagramPacket acknowledgement = new  DatagramPacket(ackPacket, ackPacket.length, address, port);
            socket.send(acknowledgement);
            System.out.println("Sent ack: Sequence Number = " + lastSequenceNumber);
        }

}