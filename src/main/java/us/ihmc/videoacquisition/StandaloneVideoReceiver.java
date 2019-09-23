package us.ihmc.videoacquisition;

import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

import controller_msgs.msg.dds.VideoPacket;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import us.ihmc.pubsub.DomainFactory.PubSubImplementation;
import us.ihmc.ros2.RealtimeRos2Node;
import us.ihmc.ros2.Ros2Distro;
import us.ihmc.util.PeriodicNonRealtimeThreadSchedulerFactory;
import us.ihmc.util.PeriodicThreadSchedulerFactory;

public class StandaloneVideoReceiver extends Application
{
   private String name = "video_receiver";
   private String namespace = "/us/ihmc";
   private ImageView viewport;

   private final AtomicReference<VideoPacket> lastVideoPacket = new AtomicReference<>(null);
   private AnimationTimer refreshImage = new AnimationTimer()
   {
      @Override
      public void handle(long now)
      {
         updateVideoFeed();
      }
   };

   @Override
   public void start(Stage primaryStage) throws Exception
   {
      PeriodicThreadSchedulerFactory threadFactory = new PeriodicNonRealtimeThreadSchedulerFactory();
      ros2Node = new RealtimeRos2Node(PubSubImplementation.FAST_RTPS, Ros2Distro.ARDENT, threadFactory, name, namespace);

      ros2Node.createCallbackSubscription(VideoPacket.getPubSubType().get(),
                                          VideoManager.LOGGING_CAMERA_VIDEO_TOPIC,
                                          s -> lastVideoPacket.set(s.takeNextData()));

      viewport = new ImageView();
      AnchorPane root = new AnchorPane(viewport);
      AnchorPane.setTopAnchor(viewport, 0.0);
      AnchorPane.setLeftAnchor(viewport, 0.0);
      AnchorPane.setRightAnchor(viewport, 0.0);
      AnchorPane.setBottomAnchor(viewport, 0.0);
      primaryStage.setScene(new Scene(root, 600, 400));
      primaryStage.setOnCloseRequest(e -> stop());
      primaryStage.show();

      refreshImage.start();
      ros2Node.spin();
   }

   private final JPEGDecompressor decompressor = new JPEGDecompressor();
   private RealtimeRos2Node ros2Node;

   private void updateVideoFeed()
   {
      VideoPacket newPacket = lastVideoPacket.getAndSet(null);
      if (newPacket == null)
         return;

      BufferedImage bufferedImage = decompressor.decompressJPEGDataToBufferedImage(newPacket.getData().toArray());
      WritableImage fxImage = SwingFXUtils.toFXImage(bufferedImage, null);
      viewport.setImage(fxImage);
   }

   @Override
   public void stop()
   {
      refreshImage.stop();
      ros2Node.destroy();
      Platform.exit();
   }

   public static void main(String[] args)
   {
      launch(args);
   }
}
