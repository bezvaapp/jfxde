package dev.jfxde.apps.webbrowser;

import java.util.Set;

import dev.jfxde.api.AppContext;
import dev.jfxde.fonts.Fonts;
import dev.jfxde.jfx.scene.control.AutoCompleteField;
import dev.jfxde.jfx.util.FXResourceBundle;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.TilePane;

public class WebMenuBar extends BorderPane {

	private AppContext context;
	private WebPageView webPageView;
	private Button back = new Button(Fonts.FontAwesome.ARROW_LEFT);
	private Button forward = new Button(Fonts.FontAwesome.ARROW_RIGHT);
	private Button reload = new Button(Fonts.FontAwesome.REDO);
	private AutoCompleteField<String> urlField;
	private Set<String> locations;

	public WebMenuBar(AppContext context, WebPageView webPageView, Set<String> locations) {
		this.context = context;
		this.webPageView = webPageView;
		this.locations = locations;

		setMaxHeight(USE_PREF_SIZE);
		initControls();
		setListeners();
	}

	private void initControls() {
	    urlField = new AutoCompleteField<String>(locations);

		back.getStyleClass().addAll("jd-font-awesome-solid");
		back.setTooltip(new Tooltip());
		FXResourceBundle.getBundle().put(back.getTooltip().textProperty(), "back");
		back.setOnAction(e -> webPageView.back());
		back.disableProperty().bind(webPageView.backDisableProperty());
		back.setFocusTraversable(false);

		forward.getStyleClass().addAll("jd-font-awesome-solid");
		forward.setTooltip(new Tooltip());
		FXResourceBundle.getBundle().put(forward.getTooltip().textProperty(), "forward");
		forward.setOnAction(e -> webPageView.forward());
		forward.disableProperty().bind(webPageView.forwardDisableProperty());
		forward.setFocusTraversable(false);

		reload.getStyleClass().addAll("jd-font-awesome-solid");
		reload.setTooltip(new Tooltip());
		reload.textProperty().bind(Bindings.when(webPageView.runningProperty()).then(Fonts.FontAwesome.TIMES)
				.otherwise(Fonts.FontAwesome.REDO));
		reload.getTooltip().textProperty().bind(Bindings.when(webPageView.runningProperty())
				.then(FXResourceBundle.getBundle().getStringBinding("stop")).otherwise(FXResourceBundle.getBundle().getStringBinding("reload")));
		reload.setFocusTraversable(false);

		TilePane buttonPane = new TilePane();
		buttonPane.getChildren().addAll(back, forward, reload);
		buttonPane.setMaxWidth(USE_PREF_SIZE);
		buttonPane.setMaxHeight(USE_PREF_SIZE);
		BorderPane.setAlignment(buttonPane, Pos.CENTER_LEFT);

		setLeft(buttonPane);
		setCenter(urlField);
	}

	private void setListeners() {

		webPageView.locationProperty().addListener((v, o, n) -> {

			if (n != null && !n.equals(urlField.getText())) {
				urlField.setText(n);
			}
		});

		reload.setOnAction(e -> {
			if (webPageView.isRunning()) {
				webPageView.stop();
			} else {
				webPageView.reload();
			}
		});

		urlField.setOnCompleted(t -> webPageView.load(t));
	}
}
