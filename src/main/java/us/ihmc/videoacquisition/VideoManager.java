package us.ihmc.videoacquisition;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import javax.imageio.ImageIO;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber.Exception;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;

import controller_msgs.msg.dds.VideoPacket;
import us.ihmc.codecs.generated.YUVPicture;
import us.ihmc.codecs.generated.YUVPicture.YUVSubsamplingType;
import us.ihmc.codecs.yuv.JPEGEncoder;
import us.ihmc.codecs.yuv.YUVPictureConverter;
import us.ihmc.pubsub.DomainFactory.PubSubImplementation;
import us.ihmc.ros2.RealtimeRos2Node;
import us.ihmc.ros2.RealtimeRos2Publisher;
import us.ihmc.util.PeriodicNonRealtimeThreadSchedulerFactory;
import us.ihmc.util.PeriodicThreadSchedulerFactory;

public class VideoManager
{
   private JTextField txtFieldDestinationAddr;
   private JTextField txtFieldDestinationPort;
   private CanvasFrame mainFrame;
   private BufferedImage img;

   private boolean showPreview = false;
   private boolean first = true;
   private boolean streamVideo = false;

   private PeriodicThreadSchedulerFactory threadFactory = new PeriodicNonRealtimeThreadSchedulerFactory();
   private String name = "video-publisher";
   private String namespace = "/us/ihmc";
   private int domainId = -1; // FIXME set me up
   private RealtimeRos2Node ros2Node;
   private RealtimeRos2Publisher<VideoPacket> videoPacketPublisher;
   private Java2DFrameConverter frameConverter = new Java2DFrameConverter();
   private FFmpegFrameRecorder videoStreamer;

   public VideoManager() throws IOException
   {
      ros2Node = new RealtimeRos2Node(PubSubImplementation.FAST_RTPS, threadFactory, name, namespace, domainId);
      videoPacketPublisher = ros2Node.createPublisher(VideoPacket.getPubSubType().get(), "logging_camera_video_stream");
      ros2Node.spin();

      try
      {
         img = ImageIO.read(new File("noVideo.png"));
      }
      catch (IOException e1)
      {
         e1.printStackTrace();
      }
      setupUI();

      Thread thread = new Thread()
      {
         @Override
         public void run()
         {
            OpenCVFrameGrabber grabber = new OpenCVFrameGrabber(0);
            grabber.setImageWidth(640);
            grabber.setImageHeight(480);

            try
            {
               grabber.start();

               Frame capturedFrame = null;

               while ((capturedFrame = grabber.grab()) != null)
               {
                  if (mainFrame.isVisible())
                  {
                     // Show our frame in the preview

                     if (showPreview)
                     {
                        mainFrame.showImage(capturedFrame);
                        first = true;
                     }
                     else
                     {
                        if (first)
                        {
                           first = false;
                           mainFrame.showImage(img);
                        }
                     }

                     if (streamVideo)
                     {
                        BufferedImage image = frameConverter.convert(capturedFrame);
                        VideoPacket videoPacket = toVideoPacket(image);
                        videoPacketPublisher.publish(videoPacket);
                        videoStreamer.record(capturedFrame);
                     }
                  }
               }

               mainFrame.dispose();

            }
            catch (Exception | org.bytedeco.javacv.FrameRecorder.Exception e)
            {
               e.printStackTrace();
            }
            finally
            {
               try
               {
                  grabber.close();
               }
               catch (Exception e)
               {
                  e.printStackTrace();
               }
            }
         }
      };

      thread.start();
   }

   private final YUVPictureConverter converter = new YUVPictureConverter();
   private final JPEGEncoder encoder = new JPEGEncoder();

   private VideoPacket toVideoPacket(BufferedImage image)
   {
      VideoPacket videoPacket = null;
      YUVPicture picture = converter.fromBufferedImage(image, YUVSubsamplingType.YUV420);
      try
      {
         ByteBuffer buffer = encoder.encode(picture, 75);
         byte[] data = new byte[buffer.remaining()];
         buffer.get(data);
         videoPacket = new VideoPacket();
         videoPacket.getData().add(data);
         videoPacket.setTimestamp(System.nanoTime());
      }
      catch (IOException e)
      {
         e.printStackTrace();
      }
      picture.delete();
      return videoPacket;
   }

   private void setupUI()
   {
      mainFrame = new CanvasFrame("Capture Preview");
      mainFrame.setTitle("Video Broadcast");
      mainFrame.setSize(653, 300);
      mainFrame.setLocationRelativeTo(null);
      mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

      JPanel panel = new JPanel();
      mainFrame.getContentPane().add(panel, BorderLayout.NORTH);

      JToggleButton btnStartStreaming = new JToggleButton("START STREAMING");
      btnStartStreaming.addActionListener(new ActionListener()
      {
         @Override
         public void actionPerformed(ActionEvent e)
         {
            if (btnStartStreaming.isSelected())
            {
               if (txtFieldDestinationAddr.getText().isEmpty() || txtFieldDestinationPort.getText().isEmpty())
               {
                  JOptionPane.showMessageDialog(mainFrame, "IP Address and port cannot be empty!", "Address Error", JOptionPane.ERROR_MESSAGE);
                  btnStartStreaming.setSelected(false);
               }
               else
               {

                  String cmd = "udp://@" + txtFieldDestinationAddr.getText() + ":" + txtFieldDestinationPort.getText();
                  videoStreamer = new FFmpegFrameRecorder(cmd, 1920, 1080);
                  videoStreamer.setFormat("mpegts");
                  videoStreamer.setVideoCodec(avcodec.AV_CODEC_ID_H265); // works with VLC
                  videoStreamer.setInterleaved(false);
                  videoStreamer.setVideoOption("pix_fmt", "yuv420p");
                  videoStreamer.setVideoOption("fflags", "discardcorrupt");
                  videoStreamer.setVideoOption("fflags", "genpts");
                  videoStreamer.setVideoOption("fflags", "igndts");
                  videoStreamer.setVideoOption("fflags", "nofillin");
                  videoStreamer.setVideoOption("fflags", "flush_packets");
                  videoStreamer.setVideoOption("tune", "zerolatency");
                  //						recorder.setVideoOption("preset", "normal");
                  //						recorder.setVideoOption("crf",  "" + 18);

                  videoStreamer.setFrameRate(25);
                  try
                  {
                     videoStreamer.start();
                     streamVideo = true;
                     System.out.println("Started session: " + cmd);
                  }
                  catch (org.bytedeco.javacv.FrameRecorder.Exception e1)
                  {
                     e1.printStackTrace();
                  }
               }
            }
            else
            {
               try
               {
                  videoStreamer.stop();
               }
               catch (org.bytedeco.javacv.FrameRecorder.Exception e1)
               {
                  e1.printStackTrace();
               }
            }

         }
      });
      panel.add(btnStartStreaming);

      JCheckBox chckbxShowInputImage = new JCheckBox("Show Input Image");
      panel.add(chckbxShowInputImage);

      chckbxShowInputImage.addActionListener(new ActionListener()
      {

         @Override
         public void actionPerformed(ActionEvent e)
         {
            if (chckbxShowInputImage.isSelected())
            {
               showPreview = true;
            }
            else
            {
               showPreview = false;
            }
         }
      });

      JLabel lblDestinationAddress = new JLabel("Destination Address: ");
      panel.add(lblDestinationAddress);

      txtFieldDestinationAddr = new JTextField();
      panel.add(txtFieldDestinationAddr);
      txtFieldDestinationAddr.setColumns(10);

      JLabel lblDestinationPort = new JLabel("Destination Port: ");
      panel.add(lblDestinationPort);

      txtFieldDestinationPort = new JTextField();
      panel.add(txtFieldDestinationPort);
      txtFieldDestinationPort.setColumns(10);

      JPanel panel_1 = new JPanel();
      mainFrame.getContentPane().add(panel_1, BorderLayout.SOUTH);

      JLabel lblStreamStatus = new JLabel("");
      panel_1.add(lblStreamStatus);
      mainFrame.setVisible(true);
   }

   public static void main(String args[]) throws IOException
   {
      new VideoManager();
   }

}
