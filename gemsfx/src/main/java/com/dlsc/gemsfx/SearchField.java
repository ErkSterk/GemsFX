package com.dlsc.gemsfx;

import com.dlsc.gemsfx.skins.SearchFieldSkin;
import javafx.animation.RotateTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Callback;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign.MaterialDesign;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.function.BiFunction;

/**
 * The search field is a standard text field with auto suggest capabilities
 * and a selection model for a specific type of object. This type is defined by the
 * generic type argument. The main difference to other auto suggest text fields is that
 * the main outcome of this field is an object and not just the text entered by the
 * user. Another difference is how the text field automatically finds and selects the
 * first object that matches the text typed by the user so far. A third feature of
 * this control is its ability to create new instances of the specified object type if
 * no matching object can be found in the list of objects returned by the suggestion
 * provider. This last feature allows an application to let the user either pick an
 * existing object or to create a new one on-the-fly (but only if a new item producer
 * has been set).
 *
 * <h3>Matcher</h3>
 *
 * @param <T> the type of objects to work on
 *
 * @see #setSuggestionProvider(Callback)
 * @see #setConverter(StringConverter)
 * @see #setCellFactory(Callback)
 * @see #setMatcher(BiFunction)
 * @see #setNewItemProducer(Callback)
 * @see #setComparator(Comparator)
 */
public class SearchField<T> extends Control {

    private static final String DEFAULT_STYLE_CLASS = "search-field";

    private final SearchService searchService = new SearchService();

    private TextField editor = new TextField();

    /**
     * Constructs a new spotlight field. The field will set defaults for the
     * matcher, the converter, the cell factory, and the comparator. It will
     * not set a default for the "new item" producer.
     *
     * @see #setNewItemProducer(Callback)
     */
    public SearchField() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);

        editor.textProperty().bindBidirectional(textProperty());
        editor.promptTextProperty().bindBidirectional(promptTextProperty());

        setFocusTraversable(false);
        setPlaceholder(new Label("No items found"));

        editor.focusedProperty().addListener(it -> {
            if (!editor.isFocused()) {
                commit();
                if (getSelectedItem() == null) {
                    editor.setText("");
                }
            }
        });

        addEventFilter(KeyEvent.ANY, evt -> {
            if (evt.getCode().equals(KeyCode.RIGHT) || evt.getCode().equals(KeyCode.ENTER)) {
                commit();
                evt.consume();
            } else if (evt.getCode().equals(KeyCode.LEFT)) {
                editor.positionCaret(Math.max(0, editor.getCaretPosition() - 1));
            } else if (evt.getCode().equals(KeyCode.ESCAPE)) {
                cancel();
                evt.consume();
            } else if (KeyCombination.keyCombination("shortcut+a").match(evt)) {
                editor.selectAll();
                evt.consume();
            }
        });

        setMatcher((item, searchText) -> getConverter().toString(item).startsWith(searchText.toLowerCase()));

        setConverter(new StringConverter<>() {
            @Override
            public String toString(T item) {
                if (item != null) {
                    return item.toString();
                }

                return "";
            }

            @Override
            public T fromString(String s) {
                return null;
            }
        });

        setCellFactory(view -> new SearchFieldListCell(this));

        setComparator(Comparator.comparing(Object::toString));

        fullText.bind(Bindings.createStringBinding(() -> editor.getText() + getAutoCompletedText(), editor.textProperty(), autoCompletedText));

        editor.textProperty().addListener(it -> {
            if (!committing) {
                if (StringUtils.isNotBlank(editor.getText())) {
                    searchService.restart();
                } else {
                    update(null);
                }
            }
        });

        selectedItem.addListener(it -> {
            T selectedItem = getSelectedItem();
            if (selectedItem != null) {
                String displayName = getConverter().toString(selectedItem);
                String text = editor.getText();
                if (StringUtils.startsWithIgnoreCase(displayName, text)) {
                    autoCompletedText.set(displayName.substring(text.length()));
                } else {
                    autoCompletedText.set("");
                }
            } else {
                autoCompletedText.set("");
            }
        });

        editor.textProperty().addListener(it -> autoCompletedText.set(""));

        converter.addListener(it -> {
            if (getConverter() == null) {
                throw new IllegalArgumentException("converter can not be null");
            }
        });

        cellFactory.addListener(it -> {
            if (getCellFactory() == null) {
                throw new IllegalArgumentException("cell factory can not be null");
            }
        });

        suggestionProvider.addListener(it -> {
            if (getSuggestionProvider() == null) {
                throw new IllegalArgumentException("suggestion provider can not be null");
            }
        });

        comparator.addListener(it -> {
            if (getComparator() == null) {
                throw new IllegalArgumentException("comparator can not be null");
            }
        });

        matcher.addListener(it -> {
            if (getMatcher() == null) {
                throw new IllegalArgumentException("matcher can not be null");
            }
        });

        RotateTransition rotateTransition = new RotateTransition();
        rotateTransition.nodeProperty().bind(busyGraphicProperty());
        rotateTransition.setCycleCount(RotateTransition.INDEFINITE);
        rotateTransition.setByAngle(360);
        rotateTransition.setDuration(Duration.millis(500));

        searching.addListener(it -> {
            if (searching.get()) {
                rotateTransition.play();
            } else {
                rotateTransition.stop();
            }
        });

        sceneProperty().addListener(it -> {
            if (getScene() == null) {
                rotateTransition.stop();
            }
        });

        searchService.setOnRunning(evt -> fireEvent(new SearchEvent(SearchEvent.SEARCH_STARTED, searchService.getText())));

        searchService.setOnSucceeded(evt -> {
            update(searchService.getValue());
            fireEvent(new SearchEvent(SearchEvent.SEARCH_FINISHED, searchService.getText()));
        });

        searching.bind(searchService.runningProperty());
    }

    private boolean committing;

    /**
     * Makes the field commit to the currently selected item and updates
     * the field to show the full text provided by the converter for the
     * item.
     */
    public void commit() {
        committing = true;
        try {
            T selectedItem = getSelectedItem();
            if (selectedItem != null) {
                String text = getConverter().toString(selectedItem);
                if (text != null) {
                    editor.setText(text);
                    editor.positionCaret(text.length());
                } else {
                    clear();
                }
            } else {
                clear();
            }
        } finally {
            committing = false;
        }
    }

    private class SearchEventHandlerProperty extends SimpleObjectProperty<EventHandler<SearchEvent>> {

        private final EventType<SearchEvent> eventType;

        public SearchEventHandlerProperty(final String name, final EventType<SearchEvent> eventType) {
            super(SearchField.this, name);
            this.eventType = eventType;
        }

        @Override
        protected void invalidated() {
            setEventHandler(eventType, get());
        }
    }

    private SearchEventHandlerProperty onSearchStarted;

    /**
     * An event handler that can be used to get informed whenever the field starts a search.
     * This event gets fired often while the user is still typing as the search gets reset
     * with every keystroke.
     *
     * @return the "search started" event handler
     */
    public final ObjectProperty<EventHandler<SearchEvent>> onSearchStartedProperty() {
        if (onSearchStarted == null) {
            onSearchStarted = new SearchEventHandlerProperty("onSearchStartedProperty", SearchEvent.SEARCH_STARTED);
        }

        return onSearchStarted;
    }

    public final void setOnSearchStarted(EventHandler<SearchEvent> value) {
        onSearchStartedProperty().set(value);
    }

    public final EventHandler<SearchEvent> getOnSearchStarted() {
        return onSearchStarted == null ? null : onSearchStartedProperty().get();
    }

    private SearchEventHandlerProperty onSearchFinished;


    /**
     * An event handler that can be used to get informed whenever the field finishes a search.
     *
     * @return the "search finished" event handler
     */
    public final ObjectProperty<EventHandler<SearchEvent>> onSearchFinishedProperty() {
        if (onSearchFinished == null) {
            onSearchFinished = new SearchEventHandlerProperty("onSearchFinishedProperty", SearchEvent.SEARCH_FINISHED);
        }

        return onSearchFinished;
    }

    public final void setOnSearchFinished(EventHandler<SearchEvent> value) {
        onSearchFinishedProperty().set(value);
    }

    public final EventHandler<SearchEvent> getOnSearchFinished() {
        return onSearchFinished == null ? null : onSearchFinishedProperty().get();
    }

    private final ObjectProperty<Node> graphic = new SimpleObjectProperty<>(this, "graphic", new FontIcon(MaterialDesign.MDI_MAGNIFY));

    public final Node getGraphic() {
        return graphic.get();
    }

    /**
     * Stores a node that will be shown on the field's right-hand side whenever the field is idle.
     *
     * @return the field's graphic
     */
    public final ObjectProperty<Node> graphicProperty() {
        return graphic;
    }

    public final void setGraphic(Node graphic) {
        this.graphic.set(graphic);
    }

    private final ObjectProperty<Node> busyGraphic = new SimpleObjectProperty<>(this, "busyGraphic", new FontIcon(MaterialDesign.MDI_CACHED));

    public final Node getBusyGraphic() {
        return busyGraphic.get();
    }

    /**
     * Stores a node that will be shown on the field's right side whenever a search is ongoing.
     *
     * @return the busy graphic
     */
    public final ObjectProperty<Node> busyGraphicProperty() {
        return busyGraphic;
    }

    public final void setBusyGraphic(Node busyGraphic) {
        this.busyGraphic.set(busyGraphic);
    }

    private final ReadOnlyBooleanWrapper searching = new ReadOnlyBooleanWrapper(this, "searching");

    public final boolean isSearching() {
        return searching.get();
    }

    private final BooleanProperty hidePopupWithSingleChoice = new SimpleBooleanProperty(this, "", false);

    public final boolean isHidePopupWithSingleChoice() {
        return hidePopupWithSingleChoice.get();
    }

    /**
     * Hides the popup window with the suggestion list if the list only contains a single
     * elements. The default is "false".
     *
     * @return true if the popup showing the list of suggestions will not appear if only a single choice is available
     */
    public final BooleanProperty hidePopupWithSingleChoiceProperty() {
        return hidePopupWithSingleChoice;
    }

    public final void setHidePopupWithSingleChoice(boolean hidePopupWithSingleChoice) {
        this.hidePopupWithSingleChoice.set(hidePopupWithSingleChoice);
    }

    /**
     * A flag indicating whether the asynchronous search is currently in progress.
     * This flag can be used to animate something that expresses that the search is
     * ongoing.
     *
     * @return true if the search is currently in progress
     */
    public final ReadOnlyBooleanProperty searchingProperty() {
        return searching.getReadOnlyProperty();
    }

    /**
     * Returns the text field control used for editing the text.
     *
     * @return the text field editor control
     */
    public final TextField getEditor() {
        return editor;
    }

    /**
     * Selects the given item and sets the editor's text to the string
     * provided by the converter for the item.
     *
     * @param item the selected item
     */
    public void select(T item) {
        setSelectedItem(item);
        commit();
    }

    private class SearchService extends Service<Collection<T>> {

        private String text;

        @Override
        protected Task<Collection<T>> createTask() {
            text = editor.getText();
            return new SearchTask(text);
        }

        public String getText() {
            return text;
        }
    }

    private class SearchTask extends Task<Collection<T>> {

        private final String searchText;

        public SearchTask(String searchText) {
            this.searchText = searchText;
        }

        @Override
        protected Collection<T> call() throws Exception {
            Thread.sleep(250);

            if (!isCancelled() && StringUtils.isNotBlank(searchText)) {
                return getSuggestionProvider().call(new SearchFieldSuggestionRequest() {
                    @Override
                    public boolean isCancelled() {
                        return SearchTask.this.isCancelled();
                    }

                    @Override
                    public String getUserText() {
                        return searchText;
                    }
                });
            }

            return Collections.emptyList();
        }
    }

    /**
     * Cancels the current search in progress.
     */
    public final void cancel() {
        searchService.cancel();
    }

    /**
     * Updates the control with the newly found list of suggestions. The suggestions
     * are provided by a background search service.
     *
     * @param newSuggestions the new suggestions to use for the field
     */
    protected void update(Collection<T> newSuggestions) {
        if (newSuggestions == null) {
            suggestions.clear();
            return;
        }

        suggestions.setAll(newSuggestions);

        String searchText = editor.getText();
        if (StringUtils.isNotBlank(searchText)) {
            try {
                BiFunction<T, String, Boolean> matcher = getMatcher();

                newItem.set(false);

                newSuggestions.stream().filter(item -> matcher.apply(item, searchText)).findFirst().ifPresentOrElse(item -> {
                    selectedItem.set(null);
                    selectedItem.set(item);
                }, () -> {
                    if (StringUtils.isNotBlank(searchText)) {
                        Callback<String, T> itemProducer = getNewItemProducer();
                        if (itemProducer != null) {
                            newItem.set(true);
                            selectedItem.set(itemProducer.call(searchText));
                        } else {
                            selectedItem.set(null);
                        }
                    } else {
                        selectedItem.set(null);
                    }
                });
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } else {
            selectedItem.set(null);
        }
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new SearchFieldSkin(this);
    }

    @Override
    public String getUserAgentStylesheet() {
        return SearchField.class.getResource("search-field.css").toExternalForm();
    }

    /**
     * Convenience method to invoke clear() on the text field.
     */
    public void clear() {
        getEditor().clear();
    }

    private final ListProperty<T> suggestions = new SimpleListProperty<>(this, "suggestions", FXCollections.observableArrayList());

    private final ObservableList<T> readOnlySuggestions = FXCollections.unmodifiableObservableList(suggestions);

    /**
     * Returns a read-only (unmodifiable) list of the current suggestions.
     *
     * @return the list of suggestions
     * @see #suggestionProviderProperty()
     */
    public final ObservableList<T> getSuggestions() {
        return readOnlySuggestions;
    }

    private final ReadOnlyBooleanWrapper newItem = new ReadOnlyBooleanWrapper(this, "newItem");

    public final boolean isNewItem() {
        return newItem.get();
    }

    /**
     * Determines if the selected item has been created on-the-fly via the {@link #newItemProducer}. This
     * will only ever happen if a new item producer has been specified.
     *
     * @return true if the selected item was not part of the suggestion list and has been created on-the-fly
     */
    public final ReadOnlyBooleanProperty newItemProperty() {
        return newItem.getReadOnlyProperty();
    }

    private final ObjectProperty<Callback<ListView<T>, ListCell<T>>> cellFactory = new SimpleObjectProperty<>(this, "cellFactory");

    public final Callback<ListView<T>, ListCell<T>> getCellFactory() {
        return cellFactory.get();
    }

    /**
     * A cell factory that can be used by a list view to visualize the list of suggestions.
     *
     * @return the cell factory used by the suggestion list view
     * @see #getSuggestions()
     */
    public final ObjectProperty<Callback<ListView<T>, ListCell<T>>> cellFactoryProperty() {
        return cellFactory;
    }

    public final void setCellFactory(Callback<ListView<T>, ListCell<T>> cellFactory) {
        this.cellFactory.set(cellFactory);
    }

    private final ObjectProperty<Comparator<T>> comparator = new SimpleObjectProperty<>(this, "comparator");

    public final Comparator<T> getComparator() {
        return comparator.get();
    }

    /**
     * A comparator used to sort the list of suggestions. The field will try to find a first best match
     * inside the sorted list. Internally the control uses an "inner" comparator to ensure that suggestions
     * appear based on the entered text, which means that a perfect match will always show up first and then
     * the suggests that "start" with the search string.
     *
     * @return the sorting comparator used for the suggestions list
     */
    public final ObjectProperty<Comparator<T>> comparatorProperty() {
        return comparator;
    }

    public final void setComparator(Comparator<T> comparator) {
        this.comparator.set(comparator);
    }

    private final ObjectProperty<Callback<String, T>> newItemProducer = new SimpleObjectProperty<>(this, "newItemProducer");

    public final Callback<String, T> getNewItemProducer() {
        return newItemProducer.get();
    }

    /**
     * A callback used for creating a new object on-the-fly if no item matches the search
     * text.
     *
     * @return the callback for producing a new object of the field supported object type
     */
    public final ObjectProperty<Callback<String, T>> newItemProducerProperty() {
        return newItemProducer;
    }

    public final void setNewItemProducer(Callback<String, T> newItemProducer) {
        this.newItemProducer.set(newItemProducer);
    }

    private final DoubleProperty autoCompletionGap = new SimpleDoubleProperty(this, "autoCompletionGap", 1);

    public final double getAutoCompletionGap() {
        return autoCompletionGap.get();
    }

    /**
     * Defines the gap (in pixels) between the user typed text and the autocompleted text.
     *
     * @return the gap (in pixels) between the user typed text and the autocompleted text
     */
    public final DoubleProperty autoCompletionGapProperty() {
        return autoCompletionGap;
    }

    public final void setAutoCompletionGap(double autoCompletionGap) {
        this.autoCompletionGap.set(autoCompletionGap);
    }

    private final ReadOnlyStringWrapper fullText = new ReadOnlyStringWrapper(this, "fullText");

    public final String getFullText() {
        return fullText.get();
    }

    /**
     * A read-only property containing the concatenation of the regular text of the text field and
     * the autocompleted text.
     *
     * @return the full text shown by the text field
     * @see #getText()
     * @see #getAutoCompletedText()
     */
    public final ReadOnlyStringProperty fullTextProperty() {
        return fullText.getReadOnlyProperty();
    }

    private final StringProperty text = new SimpleStringProperty(this, "text", "");

    public final String getText() {
        return text.get();
    }

    /**
     * A convenience property bound to the editor's text property.
     *
     * @return the text shown by the field
     */
    public final StringProperty textProperty() {
        return text;
    }

    public final void setText(String text) {
        this.text.set(text);
    }

    private final StringProperty promptText = new SimpleStringProperty(this, "text", "");

    public final String getPromptText() {
        return promptText.get();
    }

    /**
     * A convenience property to set the prompt text shown by the text field when no text
     * has been entered yet (e.g. "Search ...").
     *
     * @return the prompt text
     * @see TextField#promptTextProperty()
     */
    public final StringProperty promptTextProperty() {
        return promptText;
    }

    public final void setPromptText(String promptText) {
        this.promptText.set(promptText);
    }

    private final ReadOnlyStringWrapper autoCompletedText = new ReadOnlyStringWrapper(this, "autoCompletedText");

    public final String getAutoCompletedText() {
        return autoCompletedText.get();
    }

    /**
     * A read-only property containing the automatically completed text. This
     * property is completely managed by the control.
     *
     * @return the auto-completed text (e.g. "ates" after the user entered "United St" in a country search field).
     */
    public final ReadOnlyStringProperty autoCompletedTextProperty() {
        return autoCompletedText.getReadOnlyProperty();
    }

    private final ObjectProperty<BiFunction<T, String, Boolean>> matcher = new SimpleObjectProperty<>(this, "matcher");

    public final BiFunction<T, String, Boolean> getMatcher() {
        return matcher.get();
    }

    /**
     * The function that is used to determine the first item in the suggestion list that is a good match
     * for auto selection. This is normally the case if the text provided by the converter for an item starts
     * with exactly the text typed by the user. Auto selection will cause the field to automatically complete
     * the text typed by the user with the name of the match.
     *
     * @see #converterProperty()
     *
     * @return the function used for determining the best match in the suggestion list
     */
    public final ObjectProperty<BiFunction<T, String, Boolean>> matcherProperty() {
        return matcher;
    }

    public final void setMatcher(BiFunction<T, String, Boolean> matcher) {
        this.matcher.set(matcher);
    }

    private final ObjectProperty<T> selectedItem = new SimpleObjectProperty<>(this, "selectedItem");

    /**
     * Contains the currently selected item.
     *
     * @return the selected item
     */
    public final ObjectProperty<T> selectedItemProperty() {
        return selectedItem;
    }

    public final T getSelectedItem() {
        return selectedItem.get();
    }

    public final void setSelectedItem(T selectedItem) {
        this.selectedItem.set(selectedItem);
    }

    private final ObjectProperty<Callback<SearchFieldSuggestionRequest, Collection<T>>> suggestionProvider = new SimpleObjectProperty<>(this, "suggestionProvider");

    public final Callback<SearchFieldSuggestionRequest, Collection<T>> getSuggestionProvider() {
        return suggestionProvider.get();
    }

    /**
     * A callback used for looking up a list of suggestions for the current search text.
     *
     * @return #getSuggestions
     */
    public final ObjectProperty<Callback<SearchFieldSuggestionRequest, Collection<T>>> suggestionProviderProperty() {
        return suggestionProvider;
    }

    public final void setSuggestionProvider(Callback<SearchFieldSuggestionRequest, Collection<T>> suggestionProvider) {
        this.suggestionProvider.set(suggestionProvider);
    }

    private final ObjectProperty<StringConverter<T>> converter = new SimpleObjectProperty<>(this, "converter");

    public final StringConverter<T> getConverter() {
        return converter.get();
    }

    /**
     * A converter for turning the objects returned by the suggestion provider into text.
     *
     * @return the converter for turning the objects returned by the suggestion provider into text
     */
    public final ObjectProperty<StringConverter<T>> converterProperty() {
        return converter;
    }

    public final void setConverter(StringConverter<T> converter) {
        this.converter.set(converter);
    }

    // --- Placeholder Node
    private ObjectProperty<Node> placeholder;

    /**
     * The placeholder UI when no suggestions have been returned by the suggestion
     * provider.
     *
     * @return the placeholder property for the list view of the auto suggest popup
     */
    public final ObjectProperty<Node> placeholderProperty() {
        if (placeholder == null) {
            placeholder = new SimpleObjectProperty<>(this, "placeholder");
        }
        return placeholder;
    }

    public final void setPlaceholder(Node value) {
        placeholderProperty().set(value);
    }

    public final Node getPlaceholder() {
        return placeholder == null ? null : placeholder.get();
    }

    /**
     * Represents a suggestion fetch request.
     */
    public interface SearchFieldSuggestionRequest {

        /**
         * Is this request canceled?
         *
         * @return {@code true} if the request is canceled, otherwise {@code false}
         */
        boolean isCancelled();

        /**
         * Get the user text to which suggestions shall be found
         *
         * @return {@link String} containing the user text
         */
        String getUserText();
    }

    /**
     * An event type used by the {@link SearchField} to indicate the start and
     * end of searching operations.
     *
     * @see SearchField#setOnSearchStarted(EventHandler)
     * @see SearchField#setOnSearchFinished(EventHandler)
     */
    public static class SearchEvent extends Event {

        /**
         * An event that gets fired when the field starts a search.
         */
        public static final EventType<SearchEvent> SEARCH_STARTED = new EventType<>(Event.ANY, "SEARCH_STARTED");

        /**
         * An event that gets fired when the field finishes a search.
         */
        public static final EventType<SearchEvent> SEARCH_FINISHED = new EventType<>(Event.ANY, "SEARCH_FINISHED");

        private String text;

        public SearchEvent(EventType<? extends SearchEvent> eventType, String text) {
            super(eventType);
            this.text = text;
        }

        public String getText() {
            return text;
        }

        @Override
        public String toString() {
            return new ToStringBuilder(this)
                    .append("eventType", eventType)
                    .append("target", target)
                    .append("consumed", consumed)
                    .append("text", text)
                    .toString();
        }
    }

    private final ObjectProperty<Node> left = new SimpleObjectProperty<>(this, "left");

    public final Node getLeft() {
        return left.get();
    }

    /**
     * A custom node that can be shown on the left-hand side of the field.
     *
     * @return a custom node for the left-hand side (e.g. "clear" button)
     */
    public final ObjectProperty<Node> leftProperty() {
        return left;
    }

    public final void setLeft(Node left) {
        this.left.set(left);
    }

    private final ObjectProperty<Node> right = new SimpleObjectProperty<>(this, "left");

    public final Node getRight() {
        return right.get();
    }

    /**
     * A custom node that can be shown on the right-hand side of the field.
     *
     * @return a custom node for the right-hand side (e.g. "clear" button)
     */
    public final ObjectProperty<Node> rightProperty() {
        return right;
    }

    public final void setRight(Node right) {
        this.right.set(right);
    }

    private final BooleanProperty showSearchIcon = new SimpleBooleanProperty(this, "showSearchIcon", true);

    public final boolean isShowSearchIcon() {
        return showSearchIcon.get();
    }

    /**
     * Determines if the field will show an icon on the right-hand side which indicates
     * that the field is a search field.
     *
     * @return true if a search icon will be shown
     */
    public final BooleanProperty showSearchIconProperty() {
        return showSearchIcon;
    }

    public final void setShowSearchIcon(boolean showSearchIcon) {
        this.showSearchIcon.set(showSearchIcon);
    }

    /**
     * A custom list cell implementation that is capable of underlining the part
     * of the text that matches the user-typed search text. The cell uses a text flow
     * node that is composed of three text nodes. One of the text nodes will be underlined
     * and represents the user search text.
     *
     * @param <T> the type of the cell
     */
    public static class SearchFieldListCell<T> extends ListCell<T> {

        private SearchField<T> searchField;
        private TextFlow textFlow = new TextFlow();
        private Text text1 = new Text();
        private Text text2 = new Text();
        private Text text3 = new Text();

        public SearchFieldListCell(SearchField<T> searchField) {
            this.searchField = searchField;

            getStyleClass().add("search-field-list-cell");

            textFlow.getChildren().setAll(text1, text2, text3);
            text1.getStyleClass().addAll("text", "start");
            text2.getStyleClass().addAll("text", "middle");
            text3.getStyleClass().addAll("text", "end");

            setPrefWidth(0);
            setGraphic(textFlow);
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }

        @Override
        protected void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);

            if (item != null && !empty) {
                String cellText = searchField.getConverter().toString(item);
                String text = searchField.getEditor().getText();
                int index = cellText.toLowerCase().indexOf(text.toLowerCase());
                if (index >= 0) {
                    text1.setText(cellText.substring(0, index));
                    text2.setText(cellText.substring(index, index + text.length()));
                    text3.setText(cellText.substring(index + text.length()));
                } else {
                    text1.setText(cellText);
                    text2.setText("");
                    text3.setText("");
                }
            } else {
                text1.setText("");
                text2.setText("");
                text3.setText("");
            }
        }
    }
}
