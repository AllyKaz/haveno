/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.dao.proposal;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.TableGroupHeadline;
import bisq.desktop.util.BSFormatter;
import bisq.desktop.util.BsqFormatter;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.voting.ballot.Ballot;
import bisq.core.dao.voting.proposal.Proposal;
import bisq.core.locale.Res;

import javax.inject.Inject;

import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import javafx.geometry.Insets;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@FxmlView
public abstract class BaseProposalView extends ActivatableView<GridPane, Void> {

    protected final DaoFacade daoFacade;
    protected final BsqWalletService bsqWalletService;
    protected final BsqFormatter bsqFormatter;
    protected final BSFormatter btcFormatter;

    protected final ObservableList<ProposalListItem> proposalListItems = FXCollections.observableArrayList();
    protected final SortedList<ProposalListItem> sortedList = new SortedList<>(proposalListItems);
    protected final List<Node> proposalViewItems = new ArrayList<>();
    protected TableView<ProposalListItem> proposalTableView;
    protected Subscription selectedProposalSubscription;
    protected ProposalDisplay proposalDisplay;
    protected int gridRow = 0;
    protected GridPane detailsGridPane, gridPane;
    protected ProposalListItem selectedProposalListItem;
    protected ListChangeListener<Ballot> proposalListChangeListener;
    protected ChangeListener<DaoPhase.Phase> phaseChangeListener;
    protected DaoPhase.Phase currentPhase;
    protected Subscription phaseSubscription;
    private ScrollPane proposalDisplayView;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    protected BaseProposalView(DaoFacade daoFacade,
                               BsqWalletService bsqWalletService,
                               BsqFormatter bsqFormatter,
                               BSFormatter btcFormatter) {
        this.daoFacade = daoFacade;
        this.bsqWalletService = bsqWalletService;
        this.bsqFormatter = bsqFormatter;
        this.btcFormatter = btcFormatter;
    }

    @Override
    public void initialize() {
        super.initialize();
        root.getStyleClass().add("vote-root");

        detailsGridPane = new GridPane();

        proposalListChangeListener = c -> updateProposalList();
        phaseChangeListener = (observable, oldValue, newValue) -> onPhaseChanged(newValue);
    }

    @Override
    protected void activate() {
        phaseSubscription = EasyBind.subscribe(daoFacade.phaseProperty(), this::onPhaseChanged);
        selectedProposalSubscription = EasyBind.subscribe(proposalTableView.getSelectionModel().selectedItemProperty(), this::onSelectProposal);

        daoFacade.phaseProperty().addListener(phaseChangeListener);

        onPhaseChanged(daoFacade.phaseProperty().get());

        sortedList.comparatorProperty().bind(proposalTableView.comparatorProperty());

        updateProposalList();
    }

    @Override
    protected void deactivate() {
        phaseSubscription.unsubscribe();
        selectedProposalSubscription.unsubscribe();

        daoFacade.phaseProperty().removeListener(phaseChangeListener);

        sortedList.comparatorProperty().unbind();

        proposalListItems.forEach(ProposalListItem::cleanup);
        proposalTableView.getSelectionModel().clearSelection();
        selectedProposalListItem = null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Create views
    ///////////////////////////////////////////////////////////////////////////////////////////


    protected void createProposalsTableView() {
        createProposalsTableView(Res.get("dao.proposal.active.header"), -10);
    }

    protected void createProposalsTableView(String header, double top) {
        TableGroupHeadline proposalsHeadline = new TableGroupHeadline(header);
        GridPane.setRowIndex(proposalsHeadline, ++gridRow);
        GridPane.setMargin(proposalsHeadline, new Insets(top, -10, -10, -10));
        GridPane.setColumnSpan(proposalsHeadline, 2);
        root.getChildren().add(proposalsHeadline);

        proposalTableView = new TableView<>();
        proposalTableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noData")));
        proposalTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        createProposalColumns(proposalTableView);
        GridPane.setRowIndex(proposalTableView, gridRow);
        GridPane.setMargin(proposalTableView, new Insets(top + 20, -10, 5, -10));
        GridPane.setColumnSpan(proposalTableView, 2);
        GridPane.setHgrow(proposalTableView, Priority.ALWAYS);
        root.getChildren().add(proposalTableView);

        proposalTableView.setItems(sortedList);

        proposalViewItems.add(proposalsHeadline);
        proposalViewItems.add(proposalTableView);
    }

    protected void createProposalDisplay() {
        proposalDisplay = new ProposalDisplay(detailsGridPane, bsqFormatter, bsqWalletService, null);
        proposalDisplayView = proposalDisplay.getView();
        GridPane.setMargin(proposalDisplayView, new Insets(10, -10, 0, -10));
        GridPane.setRowIndex(proposalDisplayView, ++gridRow);
        GridPane.setColumnSpan(proposalDisplayView, 2);
        root.getChildren().add(proposalDisplayView);
    }

    protected void hideProposalDisplay() {
        proposalDisplay.removeAllFields();
        proposalDisplayView.setVisible(false);
        proposalDisplayView.setManaged(false);
    }

    protected void showProposalDisplay(Proposal proposal) {
        proposalDisplayView.setVisible(true);
        proposalDisplayView.setManaged(true);

        proposalDisplay.createAllFields(Res.get("dao.proposal.selectedProposal"), 0, 0, proposal.getType(),
                false, false);
        proposalDisplay.setEditable(false);
        proposalDisplay.applyProposalPayload(proposal);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void onSelectProposal(ProposalListItem item) {
        selectedProposalListItem = item;
        if (item != null)
            showProposalDisplay(item.getProposal());
        else
            hideProposalDisplay();
    }

    protected void onPhaseChanged(DaoPhase.Phase phase) {
        if (phase != null && !phase.equals(currentPhase)) {
            currentPhase = phase;
            onSelectProposal(selectedProposalListItem);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    abstract protected void updateProposalList();

    protected void doUpdateProposalList(List<Proposal> list) {
        proposalListItems.forEach(ProposalListItem::cleanup);
        proposalListItems.clear();
        proposalListItems.setAll(list.stream()
                .map(proposal -> new ProposalListItem(proposal,
                        daoFacade,
                        bsqWalletService,
                        bsqFormatter))
                .collect(Collectors.toSet()));

        if (list.isEmpty())
            hideProposalDisplay();
    }

    protected void changeProposalViewItemsVisibility(boolean value) {
        proposalViewItems.forEach(node -> {
            node.setVisible(value);
            node.setManaged(value);
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TableColumns
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void createProposalColumns(TableView<ProposalListItem> tableView) {
        TableColumn<ProposalListItem, ProposalListItem> dateColumn = new AutoTooltipTableColumn<ProposalListItem, ProposalListItem>(Res.get("shared.dateTime")) {
            {
                setMinWidth(190);
                setMaxWidth(190);
            }
        };
        dateColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        dateColumn.setCellFactory(
                new Callback<TableColumn<ProposalListItem, ProposalListItem>, TableCell<ProposalListItem,
                        ProposalListItem>>() {
                    @Override
                    public TableCell<ProposalListItem, ProposalListItem> call(
                            TableColumn<ProposalListItem, ProposalListItem> column) {
                        return new TableCell<ProposalListItem, ProposalListItem>() {
                            @Override
                            public void updateItem(final ProposalListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(bsqFormatter.formatDateTime(item.getProposal().getCreationDate()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        dateColumn.setComparator(Comparator.comparing(o3 -> o3.getProposal().getCreationDate()));
        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getColumns().add(dateColumn);
        tableView.getSortOrder().add(dateColumn);

        TableColumn<ProposalListItem, ProposalListItem> nameColumn = new AutoTooltipTableColumn<>(Res.get("shared.name"));
        nameColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        nameColumn.setCellFactory(
                new Callback<TableColumn<ProposalListItem, ProposalListItem>, TableCell<ProposalListItem,
                        ProposalListItem>>() {
                    @Override
                    public TableCell<ProposalListItem, ProposalListItem> call(
                            TableColumn<ProposalListItem, ProposalListItem> column) {
                        return new TableCell<ProposalListItem, ProposalListItem>() {
                            @Override
                            public void updateItem(final ProposalListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getProposal().getName());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        nameColumn.setComparator(Comparator.comparing(o2 -> o2.getProposal().getName()));
        tableView.getColumns().add(nameColumn);

        TableColumn<ProposalListItem, ProposalListItem> titleColumn = new AutoTooltipTableColumn<>(Res.get("dao.proposal.title"));
        titleColumn.setPrefWidth(100);
        titleColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        titleColumn.setCellFactory(
                new Callback<TableColumn<ProposalListItem, ProposalListItem>, TableCell<ProposalListItem,
                        ProposalListItem>>() {
                    @Override
                    public TableCell<ProposalListItem, ProposalListItem> call(
                            TableColumn<ProposalListItem, ProposalListItem> column) {
                        return new TableCell<ProposalListItem, ProposalListItem>() {
                            @Override
                            public void updateItem(final ProposalListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getProposal().getTitle());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        titleColumn.setComparator(Comparator.comparing(o2 -> o2.getProposal().getTitle()));
        tableView.getColumns().add(titleColumn);

        TableColumn<ProposalListItem, ProposalListItem> uidColumn = new AutoTooltipTableColumn<>(Res.get("shared.id"));
        uidColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        uidColumn.setCellFactory(
                new Callback<TableColumn<ProposalListItem, ProposalListItem>, TableCell<ProposalListItem,
                        ProposalListItem>>() {

                    @Override
                    public TableCell<ProposalListItem, ProposalListItem> call(TableColumn<ProposalListItem,
                            ProposalListItem> column) {
                        return new TableCell<ProposalListItem, ProposalListItem>() {
                            private HyperlinkWithIcon field;

                            @Override
                            public void updateItem(final ProposalListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    final Proposal proposal = item.getProposal();
                                    field = new HyperlinkWithIcon(proposal.getShortId());
                                    field.setOnAction(event -> {
                                        new ProposalDetailsWindow(bsqFormatter, bsqWalletService, proposal).show();
                                    });
                                    field.setTooltip(new Tooltip(Res.get("tooltip.openPopupForDetails")));
                                    setGraphic(field);
                                } else {
                                    setGraphic(null);
                                    if (field != null)
                                        field.setOnAction(null);
                                }
                            }
                        };
                    }
                });
        uidColumn.setComparator(Comparator.comparing(o -> o.getProposal().getUid()));
        tableView.getColumns().add(uidColumn);
    }

    protected void createConfidenceColumn(TableView<ProposalListItem> tableView) {
        TableColumn<ProposalListItem, ProposalListItem> confidenceColumn = new TableColumn<>(Res.get("shared.confirmations"));
        confidenceColumn.setMinWidth(130);
        confidenceColumn.setMaxWidth(confidenceColumn.getMinWidth());

        confidenceColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));

        confidenceColumn.setCellFactory(new Callback<TableColumn<ProposalListItem, ProposalListItem>,
                TableCell<ProposalListItem, ProposalListItem>>() {

            @Override
            public TableCell<ProposalListItem, ProposalListItem> call(TableColumn<ProposalListItem,
                    ProposalListItem> column) {
                return new TableCell<ProposalListItem, ProposalListItem>() {

                    @Override
                    public void updateItem(final ProposalListItem item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null && !empty) {
                            setGraphic(item.getTxConfidenceIndicator());
                        } else {
                            setGraphic(null);
                        }
                    }
                };
            }
        });
        confidenceColumn.setComparator(Comparator.comparing(ProposalListItem::getConfirmations));
        tableView.getColumns().add(confidenceColumn);
    }
}

