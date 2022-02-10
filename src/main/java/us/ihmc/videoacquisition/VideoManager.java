package us.ihmc.videoacquisition;

import java.awt.BorderLayout;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.swing.JFrame;
import javax.swing.JPanel;
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
import us.ihmc.ros2.ROS2Node;
import us.ihmc.ros2.ROS2Publisher;

public class VideoManager
{
   public static final String LOGGING_CAMERA_VIDEO_TOPIC = "/ihmc/video";
   private CanvasFrame mainFrame;

   private boolean started = false;

   private String name = "video_publisher";
   private String namespace = "/us/ihmc";
   private int domainId = 57; // FIXME set me up
   private ROS2Node ros2Node;
   private ROS2Publisher<VideoPacket> videoPacketPublisher;
   private Java2DFrameConverter frameConverter = new Java2DFrameConverter();
   private final OpenCVFrameGrabber grabber;

   public VideoManager() throws IOException
   {
      grabber = new OpenCVFrameGrabber(0);
      grabber.setImageWidth(640);
      grabber.setImageHeight(480);

      ros2Node = new ROS2Node(PubSubImplementation.FAST_RTPS, name, namespace, domainId);
      videoPacketPublisher = ros2Node.createPublisher(VideoPacket.getPubSubType().get(), LOGGING_CAMERA_VIDEO_TOPIC);
      ScheduledExecutorService executor = ThreadTools.newSingleDaemonThreadScheduledExecutor("video-grabber");

      setupUI();

      executor.scheduleAtFixedRate(() ->
      {
         if (!started)
            return;

         System.out.println("Yoohoo!");

         try
         {
            Frame capturedFrame = grabber.grab();

            if (capturedFrame == null)
               return;

            if (mainFrame.isVisible())
            {
               // Show our frame in the preview
               SwingUtilities.invokeLater(() -> mainFrame.showImage(capturedFrame));
            }

            BufferedImage image = frameConverter.convert(capturedFrame);
            if (image.getWidth() > 1280 / 2)
               image = resize(image, 1280 / 2, 720 / 2);
            VideoPacket videoPacket = toVideoPacket(image);
            videoPacketPublisher.publish(videoPacket);

         }
         catch (Throwable e)
         {
            e.printStackTrace();
         }
      }, 0, 100, TimeUnit.MILLISECONDS);

      executor.execute(new Runnable()
      {
         @Override
         public void run()
         {
            if (start())
            {
               started = true;
               return;
            }
            else
            {
               while (!restart())
               {
                  System.out.println("Trying to restart");
               }

               started = true;
            }
         }

         private boolean start()
         {
            try
            {
               grabber.start();
               return true;
            }
            catch (Exception e)
            {
               e.printStackTrace();
               return false;
            }
         }

         private boolean restart()
         {
            try
            {
               grabber.restart();
               return true;
            }
            catch (Exception e)
            {
               e.printStackTrace();
               return false;
            }
         }
      });

   }

   public static BufferedImage resize(BufferedImage originalImage, int newWidth, int newHeight)
   {
      Image tmp = originalImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
      BufferedImage reducedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);

      Graphics2D g2d = reducedImage.createGraphics();
      g2d.drawImage(tmp, 0, 0, null);
      g2d.dispose();

      return reducedImage;
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
         videoPacket.source = 0;

         if (data.length > videoPacket.getData().capacity())
            System.err.println("Image is too big!");

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

      JPanel panel_1 = new JPanel();
      mainFrame.getContentPane().add(panel_1, BorderLayout.SOUTH);

      mainFrame.setVisible(true);
   }

   public static void main(String args[]) throws IOException
   {
      new VideoManager();
   }

}
