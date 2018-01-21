{-# LANGUAGE CPP #-}
{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE RankNTypes #-}
{-# LANGUAGE TypeFamilies #-}
module Reflex.Dom.Internal
       (module Main, run, mainWidget, mainWidgetWithHead, mainWidgetWithCss,
        mainWidgetWithHead', mainWidgetInElementById, runApp') where

import Data.ByteString (ByteString)
import Data.Text (Text)
import Reflex.Spider (Spider)
import Reflex.Dom.Core (Widget)
import Reflex.Dom.Main as Main hiding
       (mainWidget, mainWidgetWithHead, mainWidgetWithCss,
        mainWidgetWithHead', mainWidgetInElementById, runApp')
import qualified Reflex.Dom.Main as Main
       (mainWidget, mainWidgetWithHead, mainWidgetWithCss,
        mainWidgetWithHead', mainWidgetInElementById, runApp')

#if defined(ghcjs_HOST_OS)
run :: a -> a
run = id
#elif defined(MIN_VERSION_jsaddle_wkwebview)
import Language.Javascript.JSaddle.WKWebView (runFile)
import Language.Javascript.JSaddle (JSM)
import Data.Default (def)

--TODO: Eliminate this; it is needed because otherwise the app's base
--URL will be set to "about:blank" due to jsaddleMain rather than
--jsaddleMainFile being used
run :: JSM () -> IO ()
run = runFile "index.html" "" def
#elif defined(ANDROID)
import Android.HaskellActivity
import Control.Monad
import Control.Concurrent
import Data.Default
import Data.String
import Reflex.Dom.Android.MainWidget
import System.IO
import Language.Javascript.JSaddle (JSM)

run :: JSM () -> IO ()
run jsm = do
  hSetBuffering stdout LineBuffering
  hSetBuffering stderr LineBuffering
  continueWithCallbacks $ def
    { _activityCallbacks_onCreate = \_ -> do
        a <- getHaskellActivity
        let startPage = fromString "file:///android_asset/index.html"
        startMainWidget a startPage jsm
    }
  forever $ threadDelay 1000000000
#else
import Language.Javascript.JSaddle.WebKitGTK (run)
#endif

mainWidget :: (forall x. Widget x ()) -> IO ()
mainWidget w = run $ Main.mainWidget w
{-# INLINE mainWidget #-}

mainWidgetWithHead :: (forall x. Widget x ()) -> (forall x. Widget x ()) -> IO ()
mainWidgetWithHead h b = run $ Main.mainWidgetWithHead h b
{-# INLINE mainWidgetWithHead #-}

mainWidgetWithCss :: ByteString -> (forall x. Widget x ()) -> IO ()
mainWidgetWithCss css w = run $ Main.mainWidgetWithCss css w
{-# INLINE mainWidgetWithCss #-}

mainWidgetWithHead' :: (a -> Widget () b, b -> Widget () a) -> IO ()
mainWidgetWithHead' w = run $ Main.mainWidgetWithHead' w
{-# INLINE mainWidgetWithHead' #-}

mainWidgetInElementById :: Text -> (forall x. Widget x ()) -> IO ()
mainWidgetInElementById eid w = run $ Main.mainWidgetInElementById eid w
{-# INLINE mainWidgetInElementById #-}

runApp' :: (t ~ Spider) => (forall x. AppInput t -> Widget x (AppOutput t)) -> IO ()
runApp' app = run $ Main.runApp' app
{-# INLINE runApp' #-}
