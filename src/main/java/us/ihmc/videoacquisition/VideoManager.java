package us.ihmc.videoacquisition;

import java.awt.BorderLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber.Exception;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;

import controller_msgs.msg.dds.VideoPacket;
import us.ihmc.codecs.generated.YUVPicture;
import us.ihmc.codecs.generated.YUVPicture.YUVSubsamplingType;
import us.ihmc.codecs.yuv.JPEGEncoder;
import us.ihmc.codecs.yuv.YUVPictureConverter;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.pubsub.DomainFactory.PubSubImplementation;
import us.ihmc.ros2.RealtimeRos2Node;
import us.ihmc.ros2.RealtimeRos2Publisher;
import us.ihmc.util.PeriodicNonRealtimeThreadSchedulerFactory;
import us.ihmc.util.PeriodicThreadSchedulerFactory;

public class VideoManager
{
   private CanvasFrame mainFrame;
   private BufferedImage img;

   private boolean started = false;
   private boolean showPreview = false;
   private boolean first = true;
   private boolean streamVideo = false;

   private PeriodicThreadSchedulerFactory threadFactory = new PeriodicNonRealtimeThreadSchedulerFactory();
   private String name = "video_publisher";
   private String namespace = "/us/ihmc";
   private int domainId = -1; // FIXME set me up
   private RealtimeRos2Node ros2Node;
   private RealtimeRos2Publisher<VideoPacket> videoPacketPublisher;
   private Java2DFrameConverter frameConverter = new Java2DFrameConverter();
   private final OpenCVFrameGrabber grabber;

   public VideoManager() throws IOException
   {
      grabber = new OpenCVFrameGrabber(0);
      grabber.setImageWidth(640);
      grabber.setImageHeight(480);

      ros2Node = new RealtimeRos2Node(PubSubImplementation.FAST_RTPS, threadFactory, name, namespace, domainId);
      videoPacketPublisher = ros2Node.createPublisher(VideoPacket.getPubSubType().get(), "logging_camera_video_stream");
      ros2Node.spin();
      ScheduledExecutorService executor = ThreadTools.newSingleDaemonThreadScheduledExecutor("video-grabber");

      try
      {
         img = ImageIO.read(new File("noVideo.png"));
      }
      catch (IOException e)
      {
         e.printStackTrace();
      }
      setupUI();

      executor.scheduleAtFixedRate(() ->
      {
         if (!started)
            return;

         try
         {
            Frame capturedFrame = grabber.grab();

            if (capturedFrame != null)
            {
               if (mainFrame.isVisible())
               {
                  // Show our frame in the preview

                  if (showPreview)
                  {
                     SwingUtilities.invokeLater(() -> mainFrame.showImage(capturedFrame));
                     first = true;
                  }
                  else
                  {
                     if (first)
                     {
                        first = false;
                        SwingUtilities.invokeLater(() -> mainFrame.showImage(img));
                     }
                  }

                  if (streamVideo)
                  {
                     BufferedImage image = frameConverter.convert(capturedFrame);
                     VideoPacket videoPacket = toVideoPacket(image);
                     videoPacketPublisher.publish(videoPacket);
                  }
               }
            }
         }
         catch (Exception e)
         {
            e.printStackTrace();
         }
      }, 0, 100, TimeUnit.MILLISECONDS);

      executor.execute(() ->
      {
         try
         {
            grabber.start();
            started = true;
         }
         catch (Exception e)
         {
            e.printStackTrace();
         }
      });

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
      btnStartStreaming.addActionListener(e -> streamVideo = btnStartStreaming.isSelected());
      panel.add(btnStartStreaming);

      JCheckBox chckbxShowInputImage = new JCheckBox("Show Input Image");
      panel.add(chckbxShowInputImage);

      chckbxShowInputImage.addActionListener(e -> showPreview = chckbxShowInputImage.isSelected());

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
